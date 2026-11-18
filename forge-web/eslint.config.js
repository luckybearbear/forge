import js from '@eslint/js';
import pluginVue from 'eslint-plugin-vue';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// ESLint flat config（workspace 单配置——各包 `eslint .` 向上寻此文件）。
// 格式化交给 prettier（根 lint 脚本 check），此处不含风格规则。
export default tseslint.config(
  { ignores: ['**/dist/', '**/node_modules/', '**/coverage/'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: { parser: tseslint.parser },
    },
  },
  {
    languageOptions: {
      globals: { ...globals.browser },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      // 格式化归 prettier：关闭 plugin-vue 的版式类规则
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/first-attribute-linebreak': 'off',
    },
  },
  {
    // 视图页按后端 seed component 串命名（system/tenant/index 等）——index.vue/单词名豁免
    files: ['apps/*/src/views/**/*.vue'],
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
);
