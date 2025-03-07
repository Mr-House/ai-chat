<script setup>
import { ref, reactive, onMounted } from 'vue';
import MarkdownIt from 'markdown-it';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';

// 初始化markdown-it实例，配置语法高亮
const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>';
      } catch (__) { }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>';
  }
});

// 将markdown文本转换为HTML
const renderMarkdown = (text) => {
  if (!text) return '';
  return md.render(text);
};

const visible = ref(false);
const messages = ref([]);
const inputMessage = ref('');
const currentConversationId = ref('default');
const conversations = reactive({
  default: {
    id: 'default',
    title: '新对话',
    messages: []
  }
});
const showConversationList = ref(false);
const messagesContainerRef = ref(null); // 添加消息容器的引用

// 滚动到最新消息
const scrollToBottom = () => {
  if (messagesContainerRef.value) {
    setTimeout(() => {
      messagesContainerRef.value.scrollTop = messagesContainerRef.value.scrollHeight;
    }, 50);
  }
};

const toggleDrawer = () => {
  visible.value = !visible.value;
};

const sendMessage = () => {
  if (!inputMessage.value.trim()) return;

  const newMessage = {
    type: 'user',
    content: inputMessage.value,
    timestamp: new Date().toISOString()
  };

  messages.value.push(newMessage);
  conversations[currentConversationId.value].messages.push(newMessage);

  // 如果是新对话且只有一条消息，更新对话标题
  if (conversations[currentConversationId.value].title === '新对话' &&
    conversations[currentConversationId.value].messages.length === 1) {
    conversations[currentConversationId.value].title = inputMessage.value.substring(0, 20) +
      (inputMessage.value.length > 20 ? '...' : '');
  }

  // 创建一个临时的响应消息对象，用于显示流式输出
  const botResponse = reactive({
    type: 'bot',
    content: '',
    timestamp: new Date().toISOString()
  });
  messages.value.push(botResponse);
  conversations[currentConversationId.value].messages.push(botResponse);

  // 使用fetch API替代EventSource进行流式处理
  fetch('http://localhost:8080/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      type: 'user',
      content: inputMessage.value
    })
  })
    .then(response => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      // 获取响应的reader
      const reader = response.body.getReader();

      // 处理数据流
      function processStream() {
        return reader.read().then(({ done, value }) => {
          if (done) {
            return;
          }

          // 将Uint8Array转换为字符串
          const chunk = new TextDecoder().decode(value);
          console.log('Received chunk:', chunk)
          // 处理接收到的数据块
          const lines = chunk.split('\n');
          for (const line of lines) {

            try {
              console.log('Received line:', line);
              let data
              if (line.startsWith('data:')) {
                data = JSON.parse(line.substring(5) || '{}');
              } else {
                data = JSON.parse(line || '{}');
              }
              console.log('Received data:', data);
              if (data.type === 'bot') {
                // 将新内容追加到现有内容
                botResponse.content += data.content;
                // 删除content中的null字符
                botResponse.content = botResponse.content.replace(/null/g, '');
                // 每次内容更新后滚动到底部
                scrollToBottom();
              }
            } catch (error) {
              console.error('解析流数据时出错:', error);
              // 忽略解析错误，可能是不完整的JSON
            }
          }

          // 继续读取下一个数据块
          return processStream();
        }).catch(error => {
          console.error('处理流数据时出错:', error);
        });
      }

      // 开始处理流
      return processStream();
    })
    .catch(error => {
      console.error('请求失败:', error);
      botResponse.content = `请求失败: ${error.message}`;
    });

  inputMessage.value = '';
};

const createNewConversation = () => {
  const newId = 'conv_' + Date.now();
  conversations[newId] = {
    id: newId,
    title: '新对话',
    messages: []
  };
  currentConversationId.value = newId;
  messages.value = [];
  showConversationList.value = false;
};

const selectConversation = (id) => {
  currentConversationId.value = id;
  messages.value = conversations[id].messages;
  showConversationList.value = false;
};

const toggleConversationList = () => {
  showConversationList.value = !showConversationList.value;
};
</script>

<template>
  <div class="chat-container">
    <!-- 悬浮图标 -->
    <div class="chat-icon" @click="toggleDrawer">
      <a-button type="primary" shape="circle">
        <template #icon>
          <icon-message />
        </template>
      </a-button>
    </div>

    <!-- 聊天抽屉 -->
    <a-drawer width="50%" placement="right" :visible="visible" @cancel="toggleDrawer" :footer="false" unmountOnClose>
      <template #title>
        <div class="drawer-header">
          <div class="drawer-title">
            <a-button class="menu-button" type="text" @click="toggleConversationList">
              <template #icon><icon-menu /></template>
            </a-button>
            <span>{{ conversations[currentConversationId].title }}</span>
          </div>
          <a-button type="outline" shape="circle" @click="createNewConversation">
            <template #icon><icon-plus /></template>
          </a-button>
        </div>
      </template>

      <!-- 对话列表侧边栏 -->
      <div class="conversation-sidebar" :class="{ 'show': showConversationList }">
        <div class="sidebar-header">
          <h3>历史对话</h3>
          <a-button type="primary" @click="createNewConversation">
            <template #icon><icon-plus /></template>
            新建对话
          </a-button>
        </div>
        <div class="conversation-list">
          <div v-for="(conv, id) in conversations" :key="id" class="conversation-item"
            :class="{ 'active': id === currentConversationId }" @click="selectConversation(id)">
            <icon-message class="conv-icon" />
            <span class="conv-title">{{ conv.title }}</span>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="messages-container" ref="messagesContainerRef">
        <div v-if="messages.length === 0" class="empty-state">
          <icon-robot size="64" />
          <p>开始一个新的对话吧</p>
        </div>
        <div v-else v-for="(message, index) in messages" :key="index" :class="['message-wrapper', message.type]">
          <div class="message-avatar">
            <template v-if="message.type === 'user'">
              <a-avatar :style="{ backgroundColor: '#165DFF' }">
                我
              </a-avatar>
            </template>
            <template v-else>
              <a-avatar :style="{ backgroundColor: '#722ED1' }">
                <template #icon><icon-robot /></template>
              </a-avatar>
            </template>
          </div>
          <div class="message-content">
            <div class="message-bubble">
              <!-- 用户消息直接显示文本 -->
              <template v-if="message.type === 'user'">
                {{ message.content }}
              </template>
              <!-- AI消息使用Markdown渲染 -->
              <div v-else class="markdown-content" v-html="renderMarkdown(message.content)"></div>
            </div>
            <div class="message-time">{{ new Date(message.timestamp).toLocaleTimeString() }}</div>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="input-container">
        <div class="input-wrapper">
          <a-textarea v-model="inputMessage" placeholder="请输入消息..." :auto-size="{ minRows: 1, maxRows: 4 }"
            @keypress.enter.prevent="sendMessage" />
          <a-button type="primary" shape="circle" class="send-button" @click="sendMessage">
            <template #icon><icon-send /></template>
          </a-button>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<style scoped>
.chat-container {
  position: relative;
  height: 100%;
}

.chat-icon {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 1000;
  cursor: pointer;
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-button {
  margin-right: 8px;
}

/* 对话列表侧边栏 */
.conversation-sidebar {
  position: absolute;
  left: -300px;
  top: 0;
  width: 300px;
  height: 100%;
  background-color: #f9f9f9;
  border-right: 1px solid #e8e8e8;
  transition: left 0.3s ease;
  z-index: 10;
}

.conversation-sidebar.show {
  left: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conversation-list {
  padding: 8px;
  overflow-y: auto;
  height: calc(100% - 60px);
}

.conversation-item {
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: background-color 0.2s;
}

.conversation-item:hover {
  background-color: #f0f0f0;
}

.conversation-item.active {
  background-color: #e8f3ff;
}

.conv-icon {
  color: #165DFF;
}

.conv-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

/* 消息列表 */
.messages-container {
  height: calc(100vh - 180px);
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #86909c;
  gap: 16px;
}

.message-wrapper {
  display: flex;
  margin-bottom: 16px;
  gap: 12px;
  max-width: 90%;
}

.message-wrapper.user {
  margin-left: auto;
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  display: flex;
  flex-direction: column;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  word-break: break-word;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  line-height: 1.5;
}

.user .message-bubble {
  background-color: #165DFF;
  color: white;
  border-top-right-radius: 4px;
}

.bot .message-bubble {
  background-color: #f5f5f5;
  border-top-left-radius: 4px;
}

/* Markdown内容样式 */
.markdown-content {
  font-size: 14px;
  line-height: 1.6;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  line-height: 1.25;
}

.markdown-content :deep(h1) {
  font-size: 1.5em;
}

.markdown-content :deep(h2) {
  font-size: 1.3em;
}

.markdown-content :deep(h3) {
  font-size: 1.1em;
}

.markdown-content :deep(p) {
  margin-top: 0;
  margin-bottom: 16px;
}

.markdown-content :deep(a) {
  color: #1890ff;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(code) {
  font-family: SFMono-Regular, Consolas, Liberation Mono, Menlo, monospace;
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: rgba(27, 31, 35, 0.05);
  border-radius: 3px;
}

.markdown-content :deep(pre) {
  margin-top: 0;
  margin-bottom: 16px;
  padding: 16px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  background-color: #f6f8fa;
  border-radius: 6px;
}

.markdown-content :deep(pre code) {
  padding: 0;
  margin: 0;
  font-size: 100%;
  word-break: normal;
  white-space: pre;
  background: transparent;
  border: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 2em;
  margin-top: 0;
  margin-bottom: 16px;
}

.markdown-content :deep(li) {
  margin-top: 0.25em;
}

.markdown-content :deep(blockquote) {
  padding: 0 1em;
  color: #6a737d;
  border-left: 0.25em solid #dfe2e5;
  margin: 0 0 16px 0;
}

.markdown-content :deep(img) {
  max-width: 100%;
  box-sizing: content-box;
}

.markdown-content :deep(hr) {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: #e1e4e8;
  border: 0;
}

.markdown-content :deep(table) {
  border-spacing: 0;
  border-collapse: collapse;
  margin-top: 0;
  margin-bottom: 16px;
  width: 100%;
  overflow: auto;
}

.markdown-content :deep(table th) {
  font-weight: 600;
  padding: 6px 13px;
  border: 1px solid #dfe2e5;
}

.markdown-content :deep(table td) {
  padding: 6px 13px;
  border: 1px solid #dfe2e5;
}

.markdown-content :deep(table tr) {
  background-color: #fff;
  border-top: 1px solid #c6cbd1;
}

.markdown-content :deep(table tr:nth-child(2n)) {
  background-color: #f6f8fa;
}

.message-time {
  font-size: 12px;
  color: #86909c;
  margin-top: 4px;
  align-self: flex-start;
}

/* 输入容器 */
.input-container {
  padding: 16px;
  border-top: 1px solid #e8e8e8;
  background-color: #fff;
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  position: relative;
}

.send-button {
  flex-shrink: 0;
}
</style>
