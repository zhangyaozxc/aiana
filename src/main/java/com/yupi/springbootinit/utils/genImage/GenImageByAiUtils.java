package com.yupi.springbootinit.utils.genImage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class GenImageByAiUtils {
    public static final String hostUrl = "https://spark-api.cn-huabei-1.xf-yun.com/v2.1/tti";
    public static final String appid = "8901f047";
    public static final String apiSecret = "ZjUzNjNhNGFlOGU4YjQ4NmZlNTUxMjEy";
    public static final String apiKey = "f080fbcdfe22d74cabd3423889601d32";
    public static String genImageByAi (String userInput) throws Exception {
        String authUrl = getAuthUrl(hostUrl, apiKey, apiSecret);

        String json = "{\n" +
                "  \"header\": {\n" +
                "    \"app_id\": \"" + appid + "\",\n" +
                "    \"uid\": \"" + UUID.randomUUID().toString().substring(0, 15) + "\"\n" +
                "  },\n" +
                "  \"parameter\": {\n" +
                "    \"chat\": {\n" +
                "      \"domain\": \"s291394db\",\n" +
                "      \"temperature\": 0.5,\n" +
                "      \"max_tokens\": 4096,\n" +
                "      \"width\": 1024,\n" +
                "      \"height\": 1024\n" +
                "    }\n" +
                "  },\n" +
                "  \"payload\": {\n" +
                "    \"message\": {\n" +
                "      \"text\": [\n" +
                "        {\n" +
                "          \"role\": \"user\",\n" +
                "          \"content\": \""+ userInput +"\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String res = GenImageUtils.doPostJson(authUrl, null, json);
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(res, JsonObject.class);
        JsonObject choicesObject = jsonObject.getAsJsonObject("payload").getAsJsonObject("choices");
        JsonArray textArray = choicesObject.getAsJsonArray("text");
        JsonObject firstTextObject = textArray.get(0).getAsJsonObject();
        String contentValue = firstTextObject.get("content").getAsString();
        return contentValue;
    }

    private static String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        // 时间
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        // date="Thu, 12 Oct 2023 03:05:28 GMT";
        // 拼接
        String preStr = "host: " + url.getHost() + "\n" + "date: " + date + "\n" + "POST " + url.getPath() + " HTTP/1.1";
        // System.err.println(preStr);
        // SHA256加密
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
        mac.init(spec);

        byte[] hexDigits = mac.doFinal(preStr.getBytes(StandardCharsets.UTF_8));
        // Base64加密
        String sha = Base64.getEncoder().encodeToString(hexDigits);
        // System.err.println(sha);
        // 拼接
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
        // 拼接地址
        HttpUrl httpUrl = Objects.requireNonNull(HttpUrl.parse("https://" + url.getHost() + url.getPath())).newBuilder().//
                addQueryParameter("authorization", Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8))).//
                addQueryParameter("date", date).//
                addQueryParameter("host", url.getHost()).//
                build();

        // System.err.println(httpUrl.toString());
        return httpUrl.toString();
    }
}
