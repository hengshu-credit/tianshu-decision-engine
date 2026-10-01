<template>
  <section
    v-if="visible"
    class="project-context-bar"
    aria-label="当前项目范围"
  >
    <div class="context-summary">
      <span class="context-label">当前项目</span>
      <strong>{{
        currentProject.projectName || currentProject.projectCode
      }}</strong>
      <span class="context-code">{{ currentProject.projectCode }}</span>
      <el-tag
        size="small"
        :type="currentProject.status === 1 ? 'success' : 'warning'"
      >
        {{ currentProject.status === 1 ? '已启用' : '已停用' }}
      </el-tag>
    </div>
    <div class="context-actions">
      <el-select
        v-model="selectedId"
        filterable
        remote
        :remote-method="searchProjects"
        :loading="projectsLoading"
        placeholder="切换项目"
        class="project-switcher"
        @visible-change="loadProjects"
        @change="switchProject"
      >
        <el-option
          v-for="project in projectOptions"
          :key="project.id"
          :label="`${project.projectName} · ${project.projectCode}`"
          :value="project.id"
        />
      </el-select>
      <el-button link type="primary" @click="goWorkbench">
        返回项目工作台
      </el-button>
      <el-button link @click="leaveContext">退出项目范围</el-button>
    </div>
  </section>
</template>

<script>
import { getProject, listProjects } from '@/api/project'
import { routeSupportsProjectContext } from '@/utils/projectContext'

export default {
  name: 'ProjectContextBar',
  data() {
    return {
      selectedId: null,
      projectOptions: [],
      projectsLoading: false,
      projectSearchGeneration: 0,
    }
  },
  computed: {
    currentProject() {
      return this.$store.state.currentProject
    },
    visible() {
      return (
        !!this.currentProject &&
        routeSupportsProjectContext(this.$route.path)
      )
    },
  },
  watch: {
    currentProject: {
      immediate: true,
      handler(project) {
        this.selectedId = project ? project.id : null
        if (
          project &&
          !this.projectOptions.some((item) => item.id === project.id)
        ) {
          this.projectOptions = [project, ...this.projectOptions]
        }
      },
    },
  },
  methods: {
    async loadProjects(open) {
      if (!open) return
      await this.searchProjects('')
    },
    async searchProjects(keyword) {
      const generation = ++this.projectSearchGeneration
      this.projectsLoading = true
      try {
        const response = await listProjects({
          pageNum: 1,
          pageSize: 50,
          status: 1,
          keyword: keyword || '',
        })
        if (generation !== this.projectSearchGeneration) return
        const rows = (response.data && response.data.records) || []
        const current = this.currentProject
        this.projectOptions =
          current && !rows.some((row) => row.id === current.id)
            ? [current, ...rows]
            : rows
      } catch (e) {
        if (generation !== this.projectSearchGeneration) return
        this.$message.error('加载项目列表失败')
      } finally {
        if (generation === this.projectSearchGeneration) {
          this.projectsLoading = false
        }
      }
    },
    async switchProject(projectId) {
      if (
        !projectId ||
        projectId === (this.currentProject && this.currentProject.id)
      ) {
        return
      }
      this.$store.commit('INVALIDATE_PROJECT_CONTEXT_REQUESTS')
      const generation = this.$store.state.projectContextGeneration
      try {
        const response = await getProject(projectId)
        if (generation !== this.$store.state.projectContextGeneration) return
        const project = response.data
        if (!project) throw new Error('项目不存在')
        this.$store.commit('SET_CURRENT_PROJECT', project)
        await this.$router.replace({
          path: this.$route.path,
          query: { ...this.$route.query, projectId: project.id },
        })
      } catch (e) {
        if (generation !== this.$store.state.projectContextGeneration) return
        this.selectedId = this.currentProject
          ? this.currentProject.id
          : null
        this.$message.error(e.message || '切换项目失败')
      }
    },
    goWorkbench() {
      if (!this.currentProject) return
      this.$router.push('/project/' + this.currentProject.id)
    },
    leaveContext() {
      const query = { ...this.$route.query }
      delete query.projectId
      this.$store.commit('SET_CURRENT_PROJECT', null)
      this.$router.replace({ path: this.$route.path, query })
    },
  },
}
</script>

<style lang="scss" scoped>
.project-context-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 48px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--tianshu-border-subtle);
  background: var(--tianshu-bg-soft, #f8faff);
  box-sizing: border-box;
}

.context-summary,
.context-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.context-label {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}

.context-summary strong {
  overflow: hidden;
  color: var(--tianshu-text-primary);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-code {
  color: var(--tianshu-text-tertiary);
  font-family: Consolas, monospace;
  font-size: 12px;
}

.project-switcher {
  width: 220px;
}

@media (max-width: 980px) {
  .project-context-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .context-actions {
    width: 100%;
  }
}
</style>
