<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createTenant,
  deleteTenant,
  disableTenant,
  enableTenant,
  listTenants,
  updateTenant,
} from '@forge/web-shared';
import type { TenantDTO } from '@forge/web-shared';

/**
 * 租户管理（seed component 串 system/tenant/index——页面路径与菜单字典一一对应）。
 *
 * - 数据源：GET /tenants 全量列表（后端按 code 升序，量级=租户数）——搜索为前端过滤；
 * - 操作按钮挂 v-permission="['tenant:manage']"（页级 MENU 码即按钮权限，spec §5.5）；
 * - 启用/停用/删除均 ElMessageBox confirm（停用/删除都会吊销该租户存量 token——提示文案说明）。
 */
const STATUS_TAG: Record<string, 'info' | 'success' | 'danger'> = {
  INIT: 'info',
  ENABLED: 'success',
  DISABLED: 'danger',
};

const loading = ref(false);
const tenants = ref<TenantDTO[]>([]);

const query = reactive({ code: '', name: '' });
/** 已应用条件（查询点击生效；重置=清空并立即应用）。 */
const applied = reactive({ code: '', name: '' });

/** 前端过滤（全量列表语义：code/name 均包含匹配，大小写不敏感）。 */
const filtered = computed(() =>
  tenants.value.filter((t) => {
    if (applied.code && !t.code.toLowerCase().includes(applied.code.trim().toLowerCase()))
      return false;
    if (applied.name && !t.name.toLowerCase().includes(applied.name.trim().toLowerCase()))
      return false;
    return true;
  }),
);

async function load(): Promise<void> {
  loading.value = true;
  try {
    tenants.value = await listTenants();
  } finally {
    loading.value = false;
  }
}

function search(): void {
  applied.code = query.code.trim();
  applied.name = query.name.trim();
}

function resetQuery(): void {
  query.code = '';
  query.name = '';
  applied.code = '';
  applied.name = '';
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
  packageId: '',
  expiredAt: '',
});

const rules: FormRules = {
  code: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
};

function openCreate(): void {
  editingId.value = null;
  form.code = '';
  form.name = '';
  form.packageId = '';
  form.expiredAt = '';
  dialogVisible.value = true;
}

function openEdit(row: TenantDTO): void {
  editingId.value = row.id;
  form.code = row.code; // 展示用：编辑时 code 不可改（后端 Update 不含 code）
  form.name = row.name;
  form.packageId = row.packageId ?? '';
  form.expiredAt = row.expiredAt ?? '';
  dialogVisible.value = true;
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (editingId.value) {
      await updateTenant(editingId.value, {
        name: form.name,
        packageId: form.packageId || undefined,
        expiredAt: form.expiredAt || null,
      });
      ElMessage.success('租户已更新');
    } else {
      await createTenant({
        code: form.code,
        name: form.name,
        packageId: form.packageId || undefined,
        expiredAt: form.expiredAt || undefined,
      });
      ElMessage.success('租户已创建（初始状态 INIT，需显式启用）');
    }
    dialogVisible.value = false;
    await load();
  } catch {
    /* 失败 toast 由 shared 拦截器统一弹 */
  } finally {
    saving.value = false;
  }
}

/* ---- 启用/停用/删除 ---- */
async function toggle(row: TenantDTO): Promise<void> {
  const disabling = row.status !== 'DISABLED';
  const tip = disabling
    ? '停用后该租户全部用户的存量会话将被吊销（需重新登录），确定停用？'
    : '启用后该租户用户可正常登录，确定启用？';
  try {
    await ElMessageBox.confirm(`租户「${row.name}」：${tip}`, disabling ? '停用租户' : '启用租户', {
      type: disabling ? 'warning' : 'info',
      confirmButtonText: disabling ? '停用' : '启用',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await (disabling ? disableTenant(row.id) : enableTenant(row.id));
    ElMessage.success(disabling ? '已停用' : '已启用');
    await load();
  } catch {
    /* 拦截器已 toast */
  }
}

async function remove(row: TenantDTO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除租户「${row.name}（${row.code}）」？软删除后编码可复用，该租户全部存量会话将被吊销。`,
      '删除租户',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  try {
    await deleteTenant(row.id);
    ElMessage.success('已删除');
    await load();
  } catch {
    /* 拦截器已 toast */
  }
}
</script>

<template>
  <div class="tenant-page">
    <el-card shadow="never" class="tenant-search">
      <el-form inline @submit.prevent>
        <el-form-item label="编码">
          <el-input v-model="query.code" placeholder="租户编码" clearable />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="租户名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="tenant-toolbar">
          <span>租户列表（全量 {{ filtered.length }} 条，前端过滤）</span>
          <el-button v-permission="['tenant:manage']" type="primary" @click="openCreate">
            新增租户
          </el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="filtered" row-key="id">
        <el-table-column prop="code" label="编码" min-width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status] ?? 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="packageId" label="套餐" min-width="140">
          <template #default="{ row }">{{ row.packageId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="到期时间" min-width="170">
          <template #default="{ row }">{{ row.expiredAt ?? '不过期' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="['tenant:manage']"
              link
              type="primary"
              @click="openEdit(row as TenantDTO)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="['tenant:manage']"
              link
              :type="row.status === 'DISABLED' ? 'success' : 'warning'"
              @click="toggle(row as TenantDTO)"
            >
              {{ row.status === 'DISABLED' ? '启用' : '停用' }}
            </el-button>
            <el-button
              v-permission="['tenant:manage']"
              link
              type="danger"
              @click="remove(row as TenantDTO)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑租户' : '新增租户'"
      width="520px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" :disabled="!!editingId" placeholder="如 t-mfg" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="租户名称" />
        </el-form-item>
        <el-form-item label="套餐">
          <el-input v-model="form.packageId" placeholder="套餐 id（可空）" />
        </el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker
            v-model="form.expiredAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            placeholder="可空=不过期"
          />
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
.tenant-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tenant-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
