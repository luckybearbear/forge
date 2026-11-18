import { createApp } from 'vue';
import { createPinia } from 'pinia';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import ElementPlus from 'element-plus';

import 'element-plus/dist/index.css';

import App from './App.vue';
import router from './router';
import { setupPermissionGuard } from './router/permission';
import { permissionDirective } from './directives/permission';

// user/permission store 纯内存（不 persist）——token 在 shared cache；persist 插件暂无消费方，预留装配。
const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

// 守卫注册须先于首次导航（router.install 触发的初始解析）
setupPermissionGuard(router);

createApp(App)
  .use(pinia)
  .use(router)
  .use(ElementPlus)
  .directive('permission', permissionDirective)
  .mount('#app');
