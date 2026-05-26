#include <Arduino.h>
#include "esp_camera.h"
#include <WiFi.h>
#include "esp_http_server.h"
#include <ESPmDNS.h>
#include "soc/soc.h"           // Disable brownout problems
#include "soc/rtc_cntl_reg.h"  // Disable brownout problems

// ==========================================
// WIFI CONFIGURATION - PLEASE UPDATE THESE!
// ==========================================
const char* ssid = "WiFihot";
const char* password = "88888888";

// AP Configuration (Fixed Address)
const char* ap_ssid = "ESP32-CAM-Fixed";
const char* ap_password = ""; // Empty for open network, or set a password (min 8 chars)

// AI Thinker ESP32-CAM PIN Map
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM     0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM       5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

#define LED_GPIO_NUM      33 // On-board red LED (inverted logic)

httpd_handle_t camera_httpd = NULL;
bool camera_ready = false; // Flag to check if camera init was successful

// Handler for a single still image (easier to debug than stream)
esp_err_t capture_handler(httpd_req_t *req) {
  if(!camera_ready) {
     httpd_resp_send_500(req);
     return ESP_FAIL;
  }
  camera_fb_t * fb = NULL;
  esp_err_t res = ESP_OK;
  
  fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("Capture failed: esp_camera_fb_get() returned NULL");
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }

  Serial.printf("Capture success: %u bytes, %ux%u\n", fb->len, fb->width, fb->height);

  httpd_resp_set_type(req, "image/jpeg");
  httpd_resp_set_hdr(req, "Content-Disposition", "inline; filename=capture.jpg");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Cache-Control", "no-cache, no-store, must-revalidate");
  httpd_resp_set_hdr(req, "Pragma", "no-cache");
  httpd_resp_set_hdr(req, "Expires", "0");

  res = httpd_resp_send(req, (const char *)fb->buf, fb->len);
  esp_camera_fb_return(fb);
  return res;
}

// Handler for the MJPEG stream
#define PART_BOUNDARY "123456789000000000000987654321"
#define STREAM_CONTENT_TYPE "multipart/x-mixed-replace;boundary=" PART_BOUNDARY
#define STREAM_BOUNDARY "\r\n--" PART_BOUNDARY "\r\n"
#define STREAM_PART "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n"

esp_err_t stream_handler(httpd_req_t *req) {
  if(!camera_ready) {
     httpd_resp_send_500(req);
     return ESP_FAIL;
  }
  camera_fb_t * fb = NULL;
  esp_err_t res = ESP_OK;
  size_t _jpg_buf_len = 0;
  uint8_t * _jpg_buf = NULL;
  char part_buf[128];

  Serial.println("Stream started");
  
  res = httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
  if(res != ESP_OK){
    return res;
  }

  // Add headers to improve compatibility with mobile apps and WebViews
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Cache-Control", "no-cache, no-store, must-revalidate");
  httpd_resp_set_hdr(req, "Pragma", "no-cache");
  httpd_resp_set_hdr(req, "Expires", "0");
  httpd_resp_set_hdr(req, "Connection", "close");

  while(true){
    fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("Stream frame failed: fb is NULL");
      res = ESP_FAIL;
    } else {
      if(fb->format != PIXFORMAT_JPEG){
        bool jpeg_converted = frame2jpg(fb, 80, &_jpg_buf, &_jpg_buf_len);
        esp_camera_fb_return(fb);
        fb = NULL;
        if(!jpeg_converted){
          Serial.println("JPEG compression failed");
          res = ESP_FAIL;
        }
      } else {
        _jpg_buf_len = fb->len;
        _jpg_buf = fb->buf;
      }
    }
    if(res == ESP_OK){
      size_t hlen = snprintf(part_buf, sizeof(part_buf), STREAM_BOUNDARY STREAM_PART, _jpg_buf_len);
      res = httpd_resp_send_chunk(req, (const char *)part_buf, hlen);
    }
    if(res == ESP_OK){
      res = httpd_resp_send_chunk(req, (const char *)_jpg_buf, _jpg_buf_len);
    }
    if(fb){
      esp_camera_fb_return(fb);
      fb = NULL;
      _jpg_buf = NULL;
    } else if(_jpg_buf){
      free(_jpg_buf);
      _jpg_buf = NULL;
    }
    if(res != ESP_OK){
      Serial.printf("Stream send failed: %d (Connection lost)\n", res);
      break;
    }
    vTaskDelay(pdMS_TO_TICKS(10)); 
  }
  return res;
}

// Handler for the main page
esp_err_t index_handler(httpd_req_t *req) {
  const char* html = 
    "<html><head><title>ESP32-CAM</title>"
    "<meta name='viewport' content='width=device-width, initial-scale=1, user-scalable=no'>"
    "<style>"
    "body{margin:0;padding:0;background:#000;color:#fff;font-family:sans-serif;overflow:hidden;}"
    "#live{width:100vw;height:100vh;object-fit:contain;background:#111;}"
    "</style>"
    "</head><body>"
    "<img id='live' src='/stream' onerror=\"this.src='/capture?' + Date.now(); this.onerror=null;\" />"
    "</body></html>";
  httpd_resp_set_type(req, "text/html");
  return httpd_resp_send(req, html, HTTPD_RESP_USE_STRLEN);
}

void startCameraServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = 80;

  httpd_uri_t index_uri = {
    .uri       = "/",
    .method    = HTTP_GET,
    .handler   = index_handler,
    .user_ctx  = NULL
  };

  httpd_uri_t stream_uri = {
    .uri       = "/stream",
    .method    = HTTP_GET,
    .handler   = stream_handler,
    .user_ctx  = NULL
  };
  
  httpd_uri_t capture_uri = {
    .uri       = "/capture",
    .method    = HTTP_GET,
    .handler   = capture_handler,
    .user_ctx  = NULL
  };

  Serial.printf("Starting web server on port: '%d'\n", config.server_port);
  if (httpd_start(&camera_httpd, &config) == ESP_OK) {
    httpd_register_uri_handler(camera_httpd, &index_uri);
    httpd_register_uri_handler(camera_httpd, &stream_uri);
    httpd_register_uri_handler(camera_httpd, &capture_uri);
  }
}

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
  setCpuFrequencyMhz(160);

  pinMode(LED_GPIO_NUM, OUTPUT);
  for(int i=0; i<5; i++) {
    digitalWrite(LED_GPIO_NUM, LOW);
    delay(100);
    digitalWrite(LED_GPIO_NUM, HIGH);
    delay(100);
  }

  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Serial.println();
  Serial.println("=====================================");
  Serial.println("ESP32-CAM Starting (Debug Mode)...");
  Serial.println("=====================================");

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 10000000;
  config.pixel_format = PIXFORMAT_JPEG;
  
  pinMode(PWDN_GPIO_NUM, OUTPUT);
  digitalWrite(PWDN_GPIO_NUM, HIGH);
  delay(300);
  digitalWrite(PWDN_GPIO_NUM, LOW);
  delay(300);
  
  if(psramFound()){
    config.frame_size = FRAMESIZE_QVGA;
    config.jpeg_quality = 24;
    config.fb_count = 2;
    Serial.println("PSRAM Found. Using QVGA resolution.");
  } else {
    config.frame_size = FRAMESIZE_QVGA; 
    config.jpeg_quality = 24;
    config.fb_count = 2;
    Serial.println("PSRAM Not Found. Using QVGA resolution.");
  }
  Serial.println("Resolution configuration complete.");

  Serial.println("Setting up WiFi...");
  WiFi.mode(WIFI_AP_STA);
  
  IPAddress apIP(192, 168, 4, 1);
  IPAddress apGateway(192, 168, 4, 1);
  IPAddress apSubnet(255, 255, 255, 0);
  WiFi.softAPConfig(apIP, apGateway, apSubnet);
  
  if(WiFi.softAP(ap_ssid, ap_password, 1, 0, 1)) {
    Serial.println("========================================");
    Serial.println("AP Mode Started (FIXED ADDRESS)");
    Serial.printf("SSID: %s\n", ap_ssid);
    Serial.print("Fixed IP: http://"); Serial.println(WiFi.softAPIP());
    Serial.println("========================================");
  } else {
    Serial.println("AP Mode Failed to Start!");
  }
  
  Serial.print("Connecting to WiFi Router: ");
  Serial.println(ssid);
  
  WiFi.begin(ssid, password);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  WiFi.setTxPower(WIFI_POWER_8_5dBm);
  
  int retry_count = 0;
  while (WiFi.status() != WL_CONNECTED && retry_count < 20) {
    delay(500);
    Serial.print(".");
    digitalWrite(LED_GPIO_NUM, !digitalRead(LED_GPIO_NUM));
    retry_count++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    digitalWrite(LED_GPIO_NUM, LOW);
    Serial.println("");
    Serial.println("WiFi Router Connected");
    Serial.print("Assigned IP (Variable): ");
    Serial.println(WiFi.localIP());
    Serial.print("Signal Strength (RSSI): ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
  } else {
    Serial.println("\nWiFi Router Connection Failed/Timeout.");
    Serial.println("Using AP Mode only. Please connect to 'ESP32-CAM-Fixed' to view stream.");
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x\n", err);
    camera_ready = false;
  } else {
    Serial.println("Camera Init Success!");
    camera_ready = true;
  }

  if (MDNS.begin("esp32cam")) {
    Serial.println("mDNS responder started");
    Serial.println("You can access the stream at: http://esp32cam.local");
  } else {
    Serial.println("Error setting up MDNS responder!");
  }
  
  startCameraServer();

  Serial.print("Debug Ready! Open this URL: http://");
  Serial.println(WiFi.localIP());
}

void loop() {
  Serial.println("--- Connection Status ---");
  Serial.print("Fixed Address (Connect to 'ESP32-CAM-Fixed'): http://"); 
  Serial.println(WiFi.softAPIP());
  
  if(WiFi.status() == WL_CONNECTED) {
     Serial.print("Router Address (Variable): http://");
     Serial.println(WiFi.localIP());
  } else {
     Serial.println("Router (STA) Disconnected");
  }

  digitalWrite(LED_GPIO_NUM, !digitalRead(LED_GPIO_NUM));
  delay(100);
  digitalWrite(LED_GPIO_NUM, !digitalRead(LED_GPIO_NUM));
  
  delay(2000);
}
