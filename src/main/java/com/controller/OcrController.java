package com.controller;

import com.annotation.IgnoreAuth;
import com.utils.R;
import okhttp3.*;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/ocr") // 关键路径配置
@CrossOrigin // 允许跨域请求
public class OcrController {

    private static final Logger logger = LoggerFactory.getLogger(OcrController.class);
    private static final String API_KEY = "5wcukQI1xmXFX9gKiS4sfXSe";
    private static final String SECRET_KEY = "7o1hu2NkINNgwbndYWTJDcdizx1ImMUI";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder()
            .readTimeout(300, TimeUnit.SECONDS)
            .build();

    @PostMapping("/recognize")
    @IgnoreAuth
    public ResponseEntity<?> recognize(@RequestParam("image") MultipartFile file) {
        String data = "";
        final String methodName = "recognize";
        logger.info("[{}] 收到OCR识别请求，文件名：{}", methodName, file.getOriginalFilename());

        try {
            // 1. 文件验证
            if (file.isEmpty()) {
                logger.warn("[{}] 空文件上传", methodName);
                return errorResponse("请选择要识别的图片文件");
            }

            String contentType = file.getContentType();
            if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
                logger.warn("[{}] 不支持的文件类型：{}", methodName, contentType);
                return errorResponse("仅支持JPG/PNG格式");
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                logger.warn("[{}] 文件超限：{}MB", methodName, (file.getSize()/1024/1024));
                return errorResponse("文件大小不能超过10MB");
            }

            logger.info("[{}] 文件验证通过 - 文件名：{}，类型：{}，大小：{}MB",
                    methodName,
                    file.getOriginalFilename(),
                    contentType,
                    (file.getSize()/1024/1024));

            // 2. 转换文件为Base64
            byte[] bytes = file.getBytes();
            String base64Img = Base64.getEncoder().encodeToString(bytes)
                    .replaceAll("\n", "").replaceAll("\r", "");
            logger.debug("[{}] Base64编码完成，长度：{}", methodName, base64Img.length());

            // 3. 获取百度Access Token
            String accessToken = getAccessToken();
            if (accessToken == null) {
                logger.error("[{}] Access Token获取失败", methodName);
                return errorResponse("系统繁忙，请稍后再试");
            }
            logger.info("[{}] 成功获取Access Token", methodName);

            // 4. 调用OCR接口
            String ocrUrl = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic?access_token=" + accessToken;
            RequestBody requestBody = new FormBody.Builder()
                    .add("image", base64Img)
                    .add("detect_direction", "false")
                    .add("paragraph", "false")
                    .add("probability", "false")
                    .build();

            Request request = new Request.Builder()
                    .url(ocrUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("[{}] OCR服务调用失败，HTTP状态码：{}", methodName, response.code());
                    return errorResponse("OCR服务调用失败");
                }

                // 5. 处理响应
                JSONObject jsonResponse = new JSONObject(response.body().string());
                if (jsonResponse.has("error_code")) {
                    String errorMsg = jsonResponse.getString("error_msg");
                    logger.error("[{}] 百度OCR返回错误 - 错误码：{}，信息：{}",
                            methodName,
                            jsonResponse.getInt("error_code"),
                            errorMsg);
                    return errorResponse("识别错误：" + errorMsg);
                }

                JSONArray wordsResult = jsonResponse.getJSONArray("words_result");
                if (wordsResult.length() == 0) {
                    logger.info("[{}] 未检测到文字内容", methodName);
                    return successResponse("未检测到文字");
                }

                JSONArray result = new JSONArray();
                StringBuilder fullText = new StringBuilder();
                for (int i = 0; i < wordsResult.length(); i++) {
                    String text = wordsResult.getJSONObject(i).getString("words");
                    result.put(text);
                    fullText.append(text).append("\n");

                    // 记录每个识别条目（DEBUG级别）
                    logger.debug("[{}] 识别到文字片段[{}/{}]：{}",
                            methodName,
                            i+1,
                            wordsResult.length(),
                            text);
                }

                // 记录完整识别结果（INFO级别）
                logger.info("\n[{}] 识别成功！共识别到 {} 条文字：\n{}",
                        methodName,
                        wordsResult.length(),
                        fullText.toString().trim());

                System.out.println("文本数据为：" + fullText);


                //这里就输出完内容了
                System.out.println(request);
                System.out.println(ResponseEntity.ok(result));

                System.out.println("现在输出新输入内容：");
                System.out.println(ResponseEntity.ok(R.ok().put("data", result)));
                System.out.println("Collections.singletonList的数据为：" + Collections.singletonList("未检测到文字"));
                System.out.println("result 最初的的数据是：" + result);

                return ResponseEntity.ok(R.ok().put("data", Collections.singletonList(result.toString())));

//                return ResponseEntity.ok(R.ok().put("data", Collections.singletonList("未检测到文字")));
//                return ResponseEntity.ok(R.ok().put("data", result.toString()));
//                return ResponseEntity.ok(result);
            }
        } catch (IOException e) {
            logger.error("[{}] 系统异常：{}", methodName, e.getMessage(), e);
            return errorResponse("系统错误：" + e.getMessage());
        }
    }

    // 其他方法保持不变，添加基础日志...

    private ResponseEntity<?> errorResponse(String message) {
        logger.warn("返回错误响应：{}", message);
        JSONObject error = new JSONObject();
        error.put("error", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.toString());
    }

    private ResponseEntity<?> successResponse(String message) {
        logger.info("返回成功响应：{}", message);
        JSONObject success = new JSONObject();
        success.put("result", message);
        return ResponseEntity.ok(success.toString());
    }

    private String getAccessToken() throws IOException {
        final String methodName = "getAccessToken";
        logger.debug("[{}] 开始获取Access Token", methodName);

        String tokenUrl = "https://aip.baidubce.com/oauth/2.0/token";
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", API_KEY)
                .add("client_secret", SECRET_KEY)
                .build();

        Request request = new Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("[{}] Token获取失败，HTTP状态码：{}", methodName, response.code());
                return null;
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            String accessToken = jsonResponse.getString("access_token");

            logger.info("[{}] 成功获取Access Token，有效期：{}秒",
                    methodName,
                    jsonResponse.optInt("expires_in", 0));
            return accessToken;
        }
    }

    @GetMapping("/test")
    @IgnoreAuth
    public void testThis() {
        logger.info("测试接口被调用");
    }
}



//@RestController
//@RequestMapping("/api/ocr") // 关键路径配置
//@CrossOrigin // 允许跨域请求
//public class OcrController {
//
//    //    // 从application.yml注入
////    @Value("${baidu.ocr.api-key}")
////    private String API_KEY;
////
////    @Value("${baidu.ocr.secret-key}")
////    private String SECRET_KEY;
//    private static final String API_KEY = "5wcukQI1xmXFX9gKiS4sfXSe";
//    private static final String SECRET_KEY = "7o1hu2NkINNgwbndYWTJDcdizx1ImMUI";
//
//    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder()
//            .readTimeout(300, TimeUnit.SECONDS)
//            .build();
//
//    @GetMapping("/test")
//    @IgnoreAuth // 关键注解
//    public void testThis(){
//        System.out.println("你是正确的");
//    }
//
//    @PostMapping("/recognize")
//    @IgnoreAuth // 关键注解
//    public ResponseEntity<?> recognize(@RequestParam("image") MultipartFile file) {
//        System.out.println("进入了此方法");
//        System.out.println(file.getOriginalFilename());
//        try {
//            // 1. 基础验证
//            if (file.isEmpty()) {
//                return ResponseEntity.badRequest().body(Map.of("error", "请选择要上传的图片"));
//            }
//            if (!file.getContentType().startsWith("image/")) {
//                return ResponseEntity.badRequest().body(Map.of("error", "仅支持图片文件"));
//            }
//
//            // 2. 获取Access Token
//            String accessToken = getAccessToken();
//
//            // 3. 准备请求体
//            MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
//            String imageBase64 = Base64.getEncoder().encodeToString(file.getBytes());
//            RequestBody body = RequestBody.create(
//                    "image=" + URLEncoder.encode(imageBase64, "UTF-8") + "&detect_direction=true",
//                    mediaType
//            );
//
//            // 4. 调用OCR接口
//            Request request = new Request.Builder()
//                    .url("https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic?access_token=" + accessToken)
//                    .post(body)
//                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
//                    .build();
//
//            Response response = HTTP_CLIENT.newCall(request).execute();
//            JSONObject result = new JSONObject(response.body().string());
//
//            // 5. 处理结果
//            if (result.has("error_code")) {
//                return ResponseEntity.status(500).body(Map.of(
//                        "error", "OCR识别失败",
//                        "code", result.getInt("error_code"),
//                        "message", result.getString("error_msg")
//                ));
//            }
//
//            List<String> words = result.getJSONArray("words_result")
//                    .toList()
//                    .stream()
//                    .map(item -> ((JSONObject)item).getString("words"))
//                    .collect(Collectors.toList());
//
//            return ResponseEntity.ok().body(Map.of("words_result", words));
//
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
//        }
//    }
//
//    private String getAccessToken() throws IOException {
//        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
//        RequestBody body = RequestBody.create(
//                "grant_type=client_credentials&client_id=" + API_KEY + "&client_secret=" + SECRET_KEY,
//                mediaType
//        );
//
//        Request request = new Request.Builder()
//                .url("https://aip.baidubce.com/oauth/2.0/token")
//                .post(body)
//                .addHeader("Content-Type", "application/x-www-form-urlencoded")
//                .build();
//
//        Response response = HTTP_CLIENT.newCall(request).execute();
//        return new JSONObject(response.body().string()).getString("access_token");
//    }
//}