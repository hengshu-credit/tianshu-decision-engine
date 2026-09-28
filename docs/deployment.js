(() => {
  const body = document.body
  const progress = document.querySelector('.reading-progress span')
  const backToTop = document.querySelector('#back-to-top')
  const themeToggle = document.querySelector('#theme-toggle')
  const search = document.querySelector('#manual-search')
  const sections = [...document.querySelectorAll('.deployment-section')]
  const tocLinks = [...document.querySelectorAll('#toc a')]

  const updateScrollState = () => {
    const scrollable = document.documentElement.scrollHeight - window.innerHeight
    progress.style.width = `${scrollable > 0 ? (window.scrollY / scrollable) * 100 : 0}%`
    backToTop.classList.toggle('visible', window.scrollY > 520)
    const current = sections.reduce((active, section) => (
      section.getBoundingClientRect().top <= 150 ? section.id : active
    ), sections[0]?.id)
    tocLinks.forEach(link => link.classList.toggle('active', link.getAttribute('href') === `#${current}`))
  }

  const updateThemeLabel = () => {
    themeToggle.textContent = body.classList.contains('dark') ? '☾' : '☼'
  }

  const filterSections = query => {
    const normalized = query.trim().toLowerCase()
    sections.forEach(section => {
      section.hidden = Boolean(normalized && !section.textContent.toLowerCase().includes(normalized))
    })
    updateScrollState()
  }

  const jumpToTarget = target => {
    const targetTop = target.getBoundingClientRect().top + window.scrollY
    const scrollMargin = Number.parseFloat(window.getComputedStyle(target).scrollMarginTop) || 0
    const previousScrollBehavior = document.documentElement.style.scrollBehavior
    document.documentElement.style.scrollBehavior = 'auto'
    window.scrollTo(0, Math.max(0, targetTop - scrollMargin))
    document.documentElement.style.scrollBehavior = previousScrollBehavior
  }

  const scrollToHash = () => {
    const targetId = window.location.hash.slice(1)
    if (!targetId) return
    const target = document.getElementById(targetId)
    if (target) jumpToTarget(target)
  }

  window.addEventListener('scroll', updateScrollState, { passive: true })
  window.addEventListener('resize', updateScrollState)
  backToTop.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }))
  themeToggle.addEventListener('click', () => {
    body.classList.toggle('dark')
    localStorage.setItem('tianshu-manual-theme', body.classList.contains('dark') ? 'dark' : 'light')
    updateThemeLabel()
  })
  search.addEventListener('input', event => filterSections(event.target.value))
  tocLinks.forEach(link => {
    link.addEventListener('click', event => {
      const href = link.getAttribute('href')
      const target = href ? document.querySelector(href) : null
      if (!target) return
      event.preventDefault()
      jumpToTarget(target)
      window.history.replaceState(null, '', href)
      updateScrollState()
    })
  })
  window.addEventListener('hashchange', scrollToHash)
  document.addEventListener('keydown', event => {
    if (event.key === '/' && document.activeElement !== search) {
      event.preventDefault()
      search.focus()
    }
    if (event.key === 'Escape' && document.activeElement === search) {
      search.value = ''
      filterSections('')
      search.blur()
    }
  })

  if (localStorage.getItem('tianshu-manual-theme') === 'dark') body.classList.add('dark')
  updateThemeLabel()
  updateScrollState()
  window.requestAnimationFrame(scrollToHash)
})()
