import { createStore } from 'vuex'
import expressionSessions from './modules/expressionSessions'
import workspaceTabs from './modules/workspaceTabs'
import {
  projectContextStorage,
  readProjectContext,
  writeProjectContext,
} from '@/utils/projectContext'

export default createStore({
  state: {
    currentProject: readProjectContext(projectContextStorage()),
    projectContextGeneration: 0,
  },
  mutations: {
    INVALIDATE_PROJECT_CONTEXT_REQUESTS(state) {
      state.projectContextGeneration += 1
    },
    SET_CURRENT_PROJECT(state, project) {
      state.projectContextGeneration += 1
      state.currentProject = project
      writeProjectContext(projectContextStorage(), project)
    }
  },
  actions: {},
  modules: { expressionSessions, workspaceTabs }
})
