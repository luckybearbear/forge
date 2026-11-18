<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createPermission,
  deletePermission,
  getPermissionTree,
  updatePermission,
} from '@forge/web-shared';
import type { MenuNode, PermissionType } from '@forge/web-shared';

/**
 * 权限字典（seed component 串 system/permission/index——页面路径与菜单字典一一对应）。
 *
 * - 树表：GET /permissions 默认树形返回（MenuNodeDTO 同构，children 按 sort 升序）；
 *   children 可空 → 归一化为 undefined（el-table 树列以非空数组判定展开）；
 * - 弹窗：父节点 el-tree-select（编辑时排除自身及后代防环）、type 四值 radio-group
 *   （BUTTON/API 为纯权限码——隐藏路由字段 path/component）、code/name 必填；
 * - code 不可改（后端 Update 契约无 code）；删除为软删（无引用校验，后端 README 约定）。
 */
const TYPE_TAG: Record<PermissionType, 'info' | 'success' | 'warning' | 'danger'> = {
  DIR: 'info',
  MENU: 'success',
  BUTTON: 'warning',
  API: 'danger',
};

/** 模板取色（template 不支持 TS 断言——经函数收口）。 */
function typeTag(type: string): 'info' | 'success' | 'warning' | 'danger' {
  return TYPE_TAG[type as PermissionType] ?? 'info';
}

const TYPE_OPTIONS: { value: PermissionType; label: string }[] = [
  { value: 'DIR', label: '目录' },
  { value: 'MENU', label: '菜单' },
  { value: 'BUTTON', label: '按钮' },
  { value: 'API', label: '接口' },
];

/** children: null → undefined 归一化行（el-table 树形展开以真数组为凭）。 */
interface TreeRow extends Omit<MenuNode, 'children'> {
  children?: TreeRow[];
}

const loading = ref(false);
const tree = ref<MenuNode[]>([]);

/** children: null → undefined 归一化（el-table 树形展开以真数组为凭）。 */
function normalize(nodes: MenuNode[]): TreeRow[] {
  return nodes.map((node) => {
    const children = node.children?.length ? normalize(node.children) : undefined;
    return { ...node, children };
  });
}

const rows = computed(() => normalize(tree.value));

async function load(): Promise<void> {
  loading.value = true;
  try {
    tree.value = await getPermissionTree();
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/* ---- 新增/编辑弹窗 ---- */
const dialogVisible = ref(false);
const saving = ref(false);
const editingId = ref<string | null>(null); // null=新增
const formRef = ref<FormInstance>();
const form = reactive({
  code: '',
  name: '',
  type: 'MENU' as PermissionType,
  parentId: null as string | null,
  path: '',
  component: '',
  icon: '',
  sort: null as number | null,
  visible: true,
});

const rules: FormRules = {
  code: [{ required: true, message: '请输入权限码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
};

/** BUTTON/API 为纯权限码——无路由语义（隐藏 path/component 输入）。 */
const showRouteFields = computed(() => form.type === 'DIR' || form.type === 'MENU');

/** 父节点候选：编辑时排除自身及后代（防环——后端对环为显式约定，前端先挡明显误操作）。 */
const parentOptions = computed<TreeRow[]>(() => {
  if (!editingId.value) return rows.value;
  const prune = (nodes: TreeRow[]): TreeRow[] =>
    nodes
      .filter((node) => node.id !== editingId.value)
      .map((node) => ({ ...node, children: node.children ? prune(node.children) : undefined }));
  return prune(rows.value);
});

function openCreate(parentId: string | null = null): void {
  editingId.value = null;
  form.code = '';
  form.name = '';
  form.type = 'MENU';
  form.parentId = parentId;
  form.path = '';
  form.component = '';
  form.icon = '';
  form.sort = null;
  form.visible = true;
  dialogVisible.value = true;
}

function openEdit(row: TreeRow): void {
  editingId.value = row.id;
  form.code = row.code; // 展示用：code 不可改
  form.name = row.name;
  form.type = row.type;
  form.parentId = row.parentId;
  form.path = row.path ?? '';
  form.component = row.component ?? '';
  form.icon = row.icon ?? '';
  form.sort = row.sort;
  form.visible = row.visible ?? true;
  dialogVisible.value = true;
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (editingId.value) {
      await updatePermission(editingId.value, {
        name: form.name,
        type: form.type,
        parentId: form.parentId,
        path: form.path || null,
        component: form.component || null,
        icon: form.icon || null,
        sort: form.sort,
        visible: form.visible,
      });
      ElMessage.success('权限点已更新（授权变更需用户重新登录生效）');
    } else {
      await createPermission({
        code: form.code,
        name: form.name,
        type: form.type,
        parentId: form.parentId ?? undefined,
        path: form.path || undefined,
        component: form.component || undefined,
        icon: form.icon || undefined,
        sort: form.sort ?? undefined,
        visible: form.visible,
      });
      ElMessage.success('权限点已创建');
    }
    dialogVisible.value = false;
    await load();
  } catch {
    /* 失败 toast 由 shared 拦截器统一弹 */
  } finally {
    saving.value = false;
  }
}

async function remove(row: TreeRow): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除权限点「${row.name}（${row.code}）」？软删除；若挂有子节点将成为悬挂父（后端按提升为根处理）。`,
      '删除权限点',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  try {
    await deletePermission(row.id);
    ElMessage.success('已删除');
    await load();
  } catch {
    /* 拦截器已 toast */
  }
}
</script>

<template>
  <div class="permission-page">
    <el-card shadow="never">
      <template #header>
        <div class="permission-toolbar">
          <span>权限字典树（全量含 BUTTON/API；改动需用户重新登录生效——token 快照语义）</span>
          <el-button type="primary" @click="openCreate(null)">新增权限点</el-button>
        </div>
      </template>
      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="权限码" min-width="150" />
        <el-table-column label="路由路径" min-width="150">
          <template #default="{ row }">{{ row.path ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="组件" min-width="180">
          <template #default="{ row }">{{ row.component ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="图标" width="120">
          <template #default="{ row }">{{ row.icon ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="排序" width="80">
          <template #default="{ row }">{{ row.sort ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="可见" width="80">
          <template #default="{ row }">
            <el-tag :type="row.visible ? 'success' : 'info'" size="small">
              {{ row.visible ? '可见' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as TreeRow)">编辑</el-button>
            <el-button
              v-if="row.type === 'DIR'"
              link
              type="primary"
              @click="openCreate((row as TreeRow).id)"
            >
              新增子节点
            </el-button>
            <el-button link type="danger" @click="remove(row as TreeRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑权限点' : '新增权限点'"
      width="560px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button v-for="opt in TYPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限码" prop="code">
          <el-input v-model="form.code" :disabled="!!editingId" placeholder="如 tenant:create" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="展示名称" />
        </el-form-item>
        <el-form-item label="父节点">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            clearable
            placeholder="空=根节点"
          />
        </el-form-item>
        <template v-if="showRouteFields">
          <el-form-item label="路由路径">
            <el-input v-model="form.path" placeholder="如 /system/tenants（DIR 可空）" />
          </el-form-item>
          <el-form-item label="组件">
            <el-input
              v-model="form.component"
              placeholder="如 system/tenant/index（BUTTON/API 无）"
            />
          </el-form-item>
        </template>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="EP 图标名，如 Setting（可空）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" placeholder="升序" />
        </el-form-item>
        <el-form-item label="可见">
          <el-switch v-model="form.visible" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.permission-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
