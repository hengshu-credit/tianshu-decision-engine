(() => {
  const body = document.body
  const progress = document.querySelector('.reading-progress span')
  const backToTop = document.querySelector('#back-to-top')
  const themeToggle = document.querySelector('#theme-toggle')
  const search = document.querySelector('#manual-search')
  const tocLinks = [...document.querySelectorAll('#toc a')]
  const sections = [...document.querySelectorAll('.scrolly-story, .manual-section')]
  const searchable = [...document.querySelectorAll('[data-search]')]
  const modal = document.querySelector('#image-modal')
  const modalImage = modal.querySelector('img')
  const story = document.querySelector('.scrolly-story')
  const storyStage = story?.querySelector('.story-stage')
  const storySteps = story ? [...story.querySelectorAll('.story-step')] : []
  const storyState = [
    {
      kicker: '01 · INPUT LAYER',
      title: '输入已就绪',
      description: '变量、对象字段和外部资源已经接入当前项目。',
      value: '0.98',
      latency: '08ms',
      confidence: '99.2%',
      trace: 'ARMED',
      footer: '等待规则读取输入',
      caption: 'INPUT / DATA OBJECT / VARIABLE'
    },
    {
      kicker: '02 · RULE ENGINE',
      title: '规则正在判断',
      description: '条件已命中，动作正在计算，编译结果保持可预览。',
      value: 'true',
      latency: '14ms',
      confidence: '99.8%',
      trace: 'RUNNING',
      footer: '条件命中 · 动作已计算',
      caption: 'CONDITION / ACTION / COMPILE'
    },
    {
      kicker: '03 · AUDIT LAYER',
      title: '结果可追溯',
      description: '修订号、输出和表达式追踪已经被记录，可以回到日志复核。',
      value: 'LOW',
      latency: '21ms',
      confidence: '100%',
      trace: 'READY',
      footer: '修订号已冻结 · 追踪已保存',
      caption: 'RELEASE / REVISION / TRACE'
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

  const updateStory = (index) => {
    if (!storyStage || !storyState[index]) return
    const state = storyState[index]
    storyStage.dataset.storyState = index
    storySteps.forEach(step => step.classList.toggle('is-active', step.dataset.storyStep === String(index)))
    storyStage.querySelector('[data-story-kicker]').textContent = state.kicker
    storyStage.querySelector('[data-story-title]').textContent = state.title
    storyStage.querySelector('[data-story-description]').textContent = state.description
    storyStage.querySelector('[data-story-value]').textContent = state.value
    storyStage.querySelector('[data-story-latency]').textContent = state.latency
    storyStage.querySelector('[data-story-confidence]').textContent = state.confidence
    storyStage.querySelector('[data-story-trace]').textContent = state.trace
    storyStage.querySelector('[data-story-footer]').textContent = state.footer
    storyStage.querySelector('[data-story-caption]').textContent = state.caption
    storyStage.querySelector('[data-story-progress]').textContent = `${String(index + 1).padStart(2, '0')} / 03`
  }
  if (story && storyStage && storySteps.length) {
    updateStory(0)
    const storyObserver = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) updateStory(Number(entry.target.dataset.storyStep))
      })
    }, { rootMargin: '-40% 0px -43%', threshold: 0 })
    storySteps.forEach(step => storyObserver.observe(step))
  }
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
