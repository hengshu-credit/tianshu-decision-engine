import { createStore } from 'vuex'
import expressionSessions from './modules/expressionSessions'
import workspaceTabs from './modules/workspaceTabs'

export default createStore({
  state: {
    currentProject: null
  },
  mutations: {
    SET_CURRENT_PROJECT(state, project) {
      state.currentProject = project
    }
  },
  actions: {},
  modules: { expressionSessions, workspaceTabs }
})
