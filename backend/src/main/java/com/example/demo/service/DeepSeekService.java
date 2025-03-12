package com.example.demo.service;

import com.example.demo.model.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DeepSeekService {

  private final String API_KEY = "";
  private final String API_ENDPOINT = "https://api.deepseek.com/v1/chat/completions";
  private final RestTemplate restTemplate = new RestTemplate();

  /**
   * 生成非流式响应
   */
  public Message generateResponse(Message userMessage) {
    try {
      // 准备HTTP请求参数
      org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("Authorization", "Bearer " + API_KEY);

      // 构建请求体
      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("model", "deepseek-reasoner");
      requestBody.put("messages", Arrays.asList(
          Map.of(
              "role", "user",
              "content", userMessage.getContent())));
      requestBody.put("temperature", 0.7);

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

      // 发送请求
      ResponseEntity<Map> response = restTemplate.postForEntity(
          API_ENDPOINT,
          request,
          Map.class);

      // 处理响应
      Map<String, Object> responseBody = response.getBody();
      String generatedContent = ((Map) ((List) responseBody.get("choices")).get(0))
          .get("message").toString();

      Message botResponse = new Message();
      botResponse.setType("bot");
      botResponse.setContent(generatedContent);
      return botResponse;

    } catch (Exception e) {
      Message errorResponse = new Message();
      errorResponse.setType("bot");
      errorResponse.setContent("抱歉，处理您的请求时出现错误：" + e.getMessage());
      return errorResponse;
    }
  }

  /**
   * 生成流式响应
   * 
   * @param userMessage 用户消息
   * @param emitter     SSE发射器
   */
  public void generateStreamResponse(Message userMessage, SseEmitter emitter) {
    try {
      // 准备HTTP请求参数
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("Authorization", "Bearer " + API_KEY);

      // 构建请求体
      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("model", "deepseek-reasoner");
      requestBody.put("messages", Arrays.asList(
          Map.of(
              "role", "user",
              "content", userMessage.getContent())));
      requestBody.put("temperature", 0.7);
      requestBody.put("stream", true); // 启用流式输出

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

      // 使用ResponseExtractor处理流式响应
      restTemplate.execute(
          API_ENDPOINT,
          org.springframework.http.HttpMethod.POST,
          req -> {
            req.getHeaders().putAll(headers);
            req.getBody().write(
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter()
                    .getObjectMapper().writeValueAsString(requestBody).getBytes());
          },
          new SSEResponseExtractor(emitter));

    } catch (Exception e) {
      try {
        Message errorResponse = new Message();
        errorResponse.setType("bot");
        errorResponse.setContent("抱歉，处理您的请求时出现错误：" + e.getMessage());
        emitter.send(errorResponse);
        emitter.complete();
      } catch (IOException ex) {
        emitter.completeWithError(ex);
      }
    }
  }

  /**
   * SSE响应提取器
   */
  private static class SSEResponseExtractor implements ResponseExtractor<Void> {
    private final SseEmitter emitter;
    private final StringBuilder contentBuilder = new StringBuilder();

    public SSEResponseExtractor(SseEmitter emitter) {
      this.emitter = emitter;
    }

    @Override
    public Void extractData(ClientHttpResponse response) throws IOException {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("data: ")) {
            String data = line.substring(6);
            if ("[DONE]".equals(data)) {
              // 流结束
              Message completeMessage = new Message();
              completeMessage.setType("bot");
              completeMessage.setContent(contentBuilder.toString());
              emitter.send(completeMessage);
              emitter.complete();
              break;
            } else {
              try {
                // 解析JSON数据
                org.springframework.boot.json.JsonParser jsonParser = new org.springframework.boot.json.JacksonJsonParser();
                Map<String, Object> jsonData = jsonParser.parseMap(data);

                if (jsonData.containsKey("choices")) {
                  List<Map<String, Object>> choices = (List<Map<String, Object>>) jsonData.get("choices");
                  if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    if (choice.containsKey("delta")) {
                      Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                      if (delta.containsKey("content")) {
                        String content = (String) delta.get("content");
                        contentBuilder.append(content);

                        // 发送增量内容
                        Message incrementalMessage = new Message();
                        incrementalMessage.setType("bot");
                        incrementalMessage.setContent(content);
                        emitter.send(incrementalMessage, MediaType.APPLICATION_JSON);
                      }
                    }
                  }
                }
              } catch (Exception e) {
                // 解析错误，继续处理下一行
              }
            }
          }
        }
      } catch (Exception e) {
        emitter.completeWithError(e);
      }
      return null;
    }
  }
}
