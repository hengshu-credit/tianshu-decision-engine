(() => {
  const body = document.body
  const progress = document.querySelector('.reading-progress span')
  const backToTop = document.querySelector('#back-to-top')
  const themeToggle = document.querySelector('#theme-toggle')
  const search = document.querySelector('#manual-search')
  const tocLinks = [...document.querySelectorAll('#toc a')]
  const sections = [...document.querySelectorAll('.hero, .manual-section')]
  const searchable = [...document.querySelectorAll('[data-search]')]
  const modal = document.querySelector('#image-modal')
  const modalImage = modal.querySelector('img')
  const workflow = document.querySelector('.workflow-scrolly')
  const workflowStage = workflow?.querySelector('.workflow-stage')
  const workflowSteps = workflow ? [...workflow.querySelectorAll('.workflow-step')] : []
  const workflowNodes = workflow ? [...workflow.querySelectorAll('.workflow-node')] : []
  const workflowLines = workflow ? [...workflow.querySelectorAll('.workflow-rail-line')] : []
  const workflowState = [
    {
      kicker: '01 · NAVIGATION LAYER',
      title: '先找到正确的入口',
      description: '从标题栏一级菜单进入项目、规则、变量与运行治理，每个入口都保持清晰可见。',
      stat: '13 个一级入口',
      tag: 'ENTRY MAP',
      footer: '下一步：确认项目上下文',
      caption: 'MAP / PROJECT / RULE / RUNTIME'
    },
    {
      kicker: '02 · PROJECT ACCESS',
      title: '把配置放进正确的项目',
      description: '确认账户权限、项目边界和访问凭证，让后续规则、资源和日志落在同一条业务链路。',
      stat: 'PROJECT / TOKEN',
      tag: 'CONTEXT READY',
      footer: '下一步：准备输入资源',
      caption: 'PROJECT / AUTH / WORKSPACE'
    },
    {
      kicker: '03 · DATA RESOURCES',
      title: '准备可以被复用的数据',
      description: '变量、数据对象、名单、外数、数据库、模型和函数成为规则可引用的输入层。',
      stat: 'ID / API / MODEL',
      tag: 'INPUTS READY',
      footer: '下一步：进入规则设计器',
      caption: 'VARIABLE / API / DATABASE / MODEL'
    },
    {
      kicker: '04 · DECISION DESIGN',
      title: '把判断编排成可测试的规则',
      description: '在九类设计器中配置条件、动作和输出，保存并编译后用样例确认每个命中路径。',
      stat: '9 DESIGNERS',
      tag: 'COMPILED',
      footer: '下一步：提交预检与审核',
      caption: 'CONDITION / ACTION / TRACE'
    },
    {
      kicker: '05 · RUNTIME GOVERNANCE',
      title: '让上线结果持续有证据',
      description: '通过执行日志、血缘、分流实验和账单，复核每次决策的版本、依赖、结果与成本。',
      stat: 'LOG / LINEAGE / BILLING',
      tag: 'AUDIT READY',
      footer: '流程完成：回到运行证据',
      caption: 'LOG / EXPERIMENT / GOVERNANCE'
    }
  ]
  const closeModal = () => {
    modal.hidden = true
    modalImage.removeAttribute('src')
    body.style.overflow = ''
  }

  const updateScrollState = () => {
    const scrollable = document.documentElement.scrollHeight - window.innerHeight
    progress.style.width = `${scrollable > 0 ? (window.scrollY / scrollable) * 100 : 0}%`
    document.documentElement.style.setProperty('--scroll-shift', Math.round(window.scrollY * 0.12))
    backToTop.classList.toggle('visible', window.scrollY > 520)
    const current = sections.reduce((active, section) => (
      section.getBoundingClientRect().top <= 150 ? section.id : active
    ), sections[0]?.id)
    tocLinks.forEach(link => link.classList.toggle('active', link.getAttribute('href') === `#${current}`))
  }

  window.addEventListener('scroll', updateScrollState, { passive: true })
  window.addEventListener('resize', updateScrollState)
  updateScrollState()
  backToTop.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }))

  const updateWorkflow = (index) => {
    if (!workflowStage || !workflowState[index]) return
    const state = workflowState[index]
    workflowStage.dataset.workflowState = index
    workflowSteps.forEach(step => step.classList.toggle('is-active', step.dataset.workflowStep === String(index)))
    workflowNodes.forEach((node, nodeIndex) => {
      node.classList.toggle('is-active', nodeIndex === index)
      node.classList.toggle('is-complete', nodeIndex < index)
    })
    workflowLines.forEach((line, lineIndex) => line.classList.toggle('is-complete', lineIndex < index))
    workflowStage.querySelector('[data-workflow-kicker]').textContent = state.kicker
    workflowStage.querySelector('[data-workflow-title]').textContent = state.title
    workflowStage.querySelector('[data-workflow-description]').textContent = state.description
    workflowStage.querySelector('[data-workflow-stat]').textContent = state.stat
    workflowStage.querySelector('[data-workflow-tag]').textContent = state.tag
    workflowStage.querySelector('[data-workflow-footer]').textContent = state.footer
    workflowStage.querySelector('[data-workflow-caption]').textContent = state.caption
    workflowStage.querySelector('[data-workflow-progress]').textContent = `${String(index + 1).padStart(2, '0')} / 05`
  }
  if (workflow && workflowStage && workflowSteps.length) {
    updateWorkflow(0)
  } else if (workflow && workflowStage) {
    updateWorkflow(0)
  }
  workflowNodes.forEach(node => {
    const selectWorkflow = () => updateWorkflow(Number(node.dataset.workflowNode))
    node.addEventListener('click', selectWorkflow)
    node.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        selectWorkflow()
      }
    })
  })
  if ('IntersectionObserver' in window) {
    const sectionObserver = new IntersectionObserver(entries => {
      entries.forEach(entry => entry.target.classList.toggle('is-inview', entry.isIntersecting))
    }, { rootMargin: '-18% 0px -62%', threshold: 0 })
    sections.forEach(section => sectionObserver.observe(section))
  }

  const savedTheme = localStorage.getItem('tianshu-manual-theme')
  if (savedTheme === 'dark') body.classList.add('dark')
  const updateThemeLabel = () => { themeToggle.textContent = body.classList.contains('dark') ? '☾' : '☼' }
  updateThemeLabel()
  themeToggle.addEventListener('click', () => {
    body.classList.toggle('dark')
    localStorage.setItem('tianshu-manual-theme', body.classList.contains('dark') ? 'dark' : 'light')
    updateThemeLabel()
  })

  const filterSections = (query) => {
    const normalized = query.trim().toLowerCase()
    searchable.forEach(item => {
      item.hidden = Boolean(normalized && !item.dataset.search.toLowerCase().includes(normalized))
    })
    sections.forEach(section => {
      const hasVisibleChild = [...section.querySelectorAll('[data-search]')].some(item => !item.hidden)
      section.hidden = Boolean(normalized && !section.dataset.search.toLowerCase().includes(normalized) && !hasVisibleChild)
    })
  }
  search.addEventListener('input', event => filterSections(event.target.value))
  document.addEventListener('keydown', event => {
    if (event.key === '/' && document.activeElement !== search) {
      event.preventDefault()
      search.focus()
    }
    if (event.key === 'Escape') {
      if (!modal.hidden) closeModal()
      else if (document.activeElement === search) { search.value = ''; filterSections(''); search.blur() }
    }
  })

  document.querySelectorAll('.zoomable').forEach(image => {
    image.addEventListener('click', () => {
      modalImage.src = image.currentSrc || image.src
      modalImage.alt = image.alt
      modal.hidden = false
      body.style.overflow = 'hidden'
    })
  })
  modal.addEventListener('click', event => { if (event.target === modal) closeModal() })
  modal.querySelector('.modal-close').addEventListener('click', closeModal)
})()
