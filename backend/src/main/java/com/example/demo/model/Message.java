package com.example.demo.model;

import lombok.Data;

@Data
public class Message {
    private String type;    // 消息类型：user/bot
    private String content; // 消息内容
}