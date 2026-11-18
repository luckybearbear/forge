import { createApp } from 'vue';
import { createPinia } from 'pinia';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import ElementPlus from 'element-plus';

import 'element-plus/dist/index.css';

import App from './App.vue';
import router from './router';

// Task 2 骨架冒烟：pinia(+persist)/router/element-plus 全量引入——布局/守卫/页面 Task 3+ 铺全。
const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

createApp(App).use(pinia).use(router).use(ElementPlus).mount('#app');
