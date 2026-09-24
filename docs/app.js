(() => {
  const body = document.body
  const progress = document.querySelector('.reading-progress span')
  const backToTop = document.querySelector('#back-to-top')
  const themeToggle = document.querySelector('#theme-toggle')
  const search = document.querySelector('#manual-search')
  const tocLinks = [...document.querySelectorAll('#toc a')]
  const sections = [...document.querySelectorAll('.manual-section')]
  const searchable = [...document.querySelectorAll('[data-search]')]
  const modal = document.querySelector('#image-modal')
  const modalImage = modal.querySelector('img')
  const closeModal = () => {
    modal.hidden = true
    modalImage.removeAttribute('src')
    body.style.overflow = ''
  }

  const updateScrollState = () => {
    const scrollable = document.documentElement.scrollHeight - window.innerHeight
    progress.style.width = `${scrollable > 0 ? (window.scrollY / scrollable) * 100 : 0}%`
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
