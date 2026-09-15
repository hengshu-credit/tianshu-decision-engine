const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const {
  createDesignerApiData,
} = require('./support/designerFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

const businessRoutes = [
  '/project',
  '/rule',
  '/variable',
  '/list',
  '/datasource',
  '/database',
  '/model',
  '/function',
  '/test',
  '/lineage',
  '/experiment',
  '/log',
  '/billing',
  '/project/1',
  '/rule/101',
  '/list/9',
  '/datasource/source/21',
  '/datasource/api/22',
  '/database/31',
  '/model/41',
  '/experiment/detail/61',
  '/datasource/source/new',
  '/datasource/api/new',
  '/database/new',
  '/experiment/new',
  '/designer/table/101',
  '/designer/tree/102',
  '/designer/flow/103',
  '/designer/ruleset/104',
  '/designer/cross/105',
  '/designer/score/106',
  '/designer/cross-adv/107',
  '/designer/score-adv/108',
  '/designer/script/109',
]

function completeApiData() {
  const routes = createDetailApiData()
  for (const [key, value] of createDesignerApiData()) {
    routes.set(key, value)
  }
  return routes
}

test('全部业务页面在密集桌面宽度下保持可读、可操作且无横向溢出', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: completeApiData(),
  })

  for (const route of businessRoutes) {
    await page.goto(`http://tianshu.local/index.html#${route}`)
    await expect(page.getByRole('main')).toBeVisible()

    const audit = await page.evaluate(() => {
      const isVisible = (element) => {
        const style = getComputedStyle(element)
        const rect = element.getBoundingClientRect()
        return (
          style.display !== 'none' &&
          style.visibility !== 'hidden' &&
          rect.width > 0 &&
          rect.height > 0
        )
      }
      const parseColor = (value) => {
        const match = String(value).match(
          /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/
        )
        return match
          ? [
              Number(match[1]),
              Number(match[2]),
              Number(match[3]),
              match[4] === undefined ? 1 : Number(match[4]),
            ]
          : null
      }
      const composite = (foreground, background) => {
        const alpha = foreground[3]
        return [
          foreground[0] * alpha + background[0] * (1 - alpha),
          foreground[1] * alpha + background[1] * (1 - alpha),
          foreground[2] * alpha + background[2] * (1 - alpha),
          1,
        ]
      }
      const effectiveBackground = (element) => {
        const layers = []
        for (let current = element; current; current = current.parentElement) {
          const color = parseColor(getComputedStyle(current).backgroundColor)
          if (color && color[3] > 0) layers.push(color)
        }
        return layers
          .reverse()
          .reduce(
            (background, foreground) =>
              composite(foreground, background),
            [255, 255, 255, 1]
          )
      }
      const luminance = (color) => {
        const channels = color.slice(0, 3).map((value) => {
          const channel = value / 255
          return channel <= 0.03928
            ? channel / 12.92
            : ((channel + 0.055) / 1.055) ** 2.4
        })
        return (
          0.2126 * channels[0] +
          0.7152 * channels[1] +
          0.0722 * channels[2]
        )
      }
      const contrastRatio = (foreground, background) => {
        const foregroundLuminance = luminance(foreground)
        const backgroundLuminance = luminance(background)
        return (
          (Math.max(foregroundLuminance, backgroundLuminance) + 0.05) /
          (Math.min(foregroundLuminance, backgroundLuminance) + 0.05)
        )
      }

      const collapsedControls = Array.from(
        document.querySelectorAll('input[placeholder], textarea[placeholder]')
      )
        .filter(isVisible)
        .filter((element) => element.getAttribute('placeholder'))
        .filter((element) => !element.closest('.el-input-number'))
        .filter((element) => element.getBoundingClientRect().width < 48)
        .map((element) => ({
          placeholder: element.getAttribute('placeholder'),
          width: Math.round(element.getBoundingClientRect().width),
        }))

      const unnamedIconButtons = Array.from(
        document.querySelectorAll('button')
      )
        .filter(isVisible)
        .filter((element) => !(element.innerText || '').trim())
        .filter(
          (element) =>
            !element.getAttribute('aria-label') &&
            !element.getAttribute('title')
        )
        .map((element) => element.className)

      const undersizedLinkButtons = Array.from(
        document.querySelectorAll('.el-button.is-link')
      )
        .filter(isVisible)
        .filter((element) => element.getBoundingClientRect().height < 28)
        .map((element) => ({
          text: (element.innerText || '').trim(),
          height: Math.round(element.getBoundingClientRect().height),
        }))

      const semanticSelector = [
        '.el-button--primary:not(.is-link)',
        '.el-button--success:not(.is-link)',
        '.el-button--warning:not(.is-link)',
        '.el-button--danger:not(.is-link)',
        '.el-tag',
        '.el-tabs__item',
        '.el-alert__title',
      ].join(',')
      const lowContrastSemanticControls = Array.from(
        document.querySelectorAll(semanticSelector)
      )
        .filter(isVisible)
        .filter((element) => !(element.disabled || element.ariaDisabled === 'true'))
        .filter((element) => (element.innerText || '').trim())
        .map((element) => {
          const background = effectiveBackground(element)
          const text = parseColor(getComputedStyle(element).color)
          const foreground = text ? composite(text, background) : background
          return {
            text: (element.innerText || '').trim().slice(0, 30),
            ratio: Number(contrastRatio(foreground, background).toFixed(2)),
            color: getComputedStyle(element).color,
            background: background
              .slice(0, 3)
              .map((value) => Math.round(value))
              .join(','),
          }
        })
        .filter(({ ratio }) => ratio < 4.5)

      return {
        rootOverflow:
          Math.max(
            document.documentElement.scrollWidth,
            document.body.scrollWidth
          ) - window.innerWidth,
        collapsedControls,
        unnamedIconButtons,
        undersizedLinkButtons,
        lowContrastSemanticControls,
      }
    })

    expect(audit.rootOverflow, `${route} 出现页面级横向溢出`).toBeLessThanOrEqual(
      1
    )
    expect(audit.collapsedControls, `${route} 存在被压扁的输入控件`).toEqual([])
    expect(
      audit.unnamedIconButtons,
      `${route} 存在无名称的图标按钮`
    ).toEqual([])
    expect(
      audit.undersizedLinkButtons,
      `${route} 存在点击热区过小的文字按钮`
    ).toEqual([])
    expect(
      audit.lowContrastSemanticControls,
      `${route} 存在对比度不足的语义控件`
    ).toEqual([])
  }

  assertClean()
})

test('关键控件的默认、hover、focus 与语义色均保持清晰反馈', async ({
  page,
}) => {
  const { assertClean } = await installDistRoutes(page, {
    apiData: completeApiData(),
  })
  await page.goto('http://tianshu.local/index.html#/project')

  const contrast = await page.evaluate(() => {
    const parseRgb = (value) => {
      const match = String(value).match(
        /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/
      )
      return match
        ? [Number(match[1]), Number(match[2]), Number(match[3])]
        : null
    }
    const luminance = (rgb) => {
      const values = rgb.map((value) => {
        const channel = value / 255
        return channel <= 0.03928
          ? channel / 12.92
          : ((channel + 0.055) / 1.055) ** 2.4
      })
      return (
        0.2126 * values[0] + 0.7152 * values[1] + 0.0722 * values[2]
      )
    }
    const ratio = (foreground, background) => {
      const first = luminance(foreground)
      const second = luminance(background)
      return (
        (Math.max(first, second) + 0.05) /
        (Math.min(first, second) + 0.05)
      )
    }

    const input = document.querySelector('input[placeholder]')
    const placeholderColor = parseRgb(
      getComputedStyle(input, '::placeholder').color
    )
    return ratio(placeholderColor, [255, 255, 255])
  })
  expect(contrast).toBeGreaterThanOrEqual(4.5)

  const queryButton = page.getByRole('button', { name: '查询', exact: true })
  const queryBefore = await queryButton.evaluate(
    (element) => getComputedStyle(element).backgroundColor
  )
  await queryButton.hover()
  await page.waitForTimeout(200)
  const queryHover = await queryButton.evaluate(
    (element) => getComputedStyle(element).backgroundColor
  )
  expect(queryHover).not.toBe(queryBefore)

  const firstInput = page.locator('input[placeholder]').first()
  await firstInput.focus()
  const focusShadow = await firstInput
    .locator('xpath=..')
    .evaluate((element) => getComputedStyle(element).boxShadow)
  expect(focusShadow).not.toBe('none')
  await page.keyboard.press('Escape')

  const firstRow = page.locator('.el-table__body tr').first()
  const firstCell = firstRow.locator('td').first()
  const rowBefore = await firstCell.evaluate(
    (element) => getComputedStyle(element).backgroundColor
  )
  await firstRow.hover()
  await page.waitForTimeout(200)
  const rowHover = await firstCell.evaluate(
    (element) => getComputedStyle(element).backgroundColor
  )
  expect(rowHover).not.toBe(rowBefore)

  assertClean()
})

test('列表操作使用稳定且可区分的主题提示色', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1600, height: 1000 })
  const { assertClean } = await installDistRoutes(page, {
    apiData: createDocsApiData(),
  })
  const pages = [
    {
      route: '/variable',
      actions: [
        ['编辑', 'warning'],
        ['详情', 'primary'],
        ['测试', 'success'],
        ['转全局', 'primary'],
        ['删除', 'danger'],
      ],
    },
    {
      route: '/rule',
      actions: [
        ['详情', 'primary'],
        ['查看', 'info'],
        ['删除', 'danger'],
      ],
    },
    {
      route: '/project/1',
      tab: '项目规则',
      actions: [
        ['详情', 'primary'],
        ['查看', 'info'],
        ['申请删除规则', 'danger'],
      ],
    },
    {
      route: '/datasource',
      actions: [
        ['编辑', 'warning'],
        ['测试', 'success'],
        ['加接口', 'primary'],
        ['删除', 'danger'],
      ],
    },
    {
      route: '/database',
      actions: [
        ['编辑', 'warning'],
        ['测试', 'success'],
        ['查询', 'primary'],
        ['删除', 'danger'],
      ],
    },
    {
      route: '/model',
      actions: [
        ['详情', 'primary'],
        ['编辑', 'warning'],
        ['重新发布', 'success'],
        ['下线', 'warning'],
        ['删除', 'danger'],
      ],
    },
    {
      route: '/function',
      actions: [
        ['测试', 'success'],
        ['编辑', 'warning'],
        ['版本', 'info'],
        ['删除', 'danger'],
      ],
    },
  ]

  for (const item of pages) {
    await page.goto(`http://tianshu.local/index.html#${item.route}`)
    await expect(page.getByRole('main')).toBeVisible()
    if (item.tab) {
      const tab = page.getByRole('tab', { name: item.tab, exact: true })
      await tab.click()
      await expect(tab).toHaveAttribute('aria-selected', 'true')
    }
    const visibleActions = page.locator(
      '.el-table__body .el-button.is-link:visible'
    )
    await expect(visibleActions.first()).toBeVisible()

    for (const [label, type] of item.actions) {
      const button = page
        .getByRole('button', { name: label, exact: true })
        .filter({ visible: true })
        .first()
      await expect(button, `${item.route} 的「${label}」缺少语义类型`).toHaveClass(
        new RegExp(`el-button--${type}`)
      )
    }

    const colors = await visibleActions.evaluateAll((buttons) => {
      const visible = buttons.filter(
        button => button.getClientRects().length > 0
      )
      return {
        normal: visible
          .filter(button => !button.classList.contains('el-button--danger'))
          .map(button => ({
            action: button.dataset.action,
            color: getComputedStyle(button).color,
          })),
        danger: visible
          .filter(button => button.classList.contains('el-button--danger'))
          .map(button => ({
            action: button.dataset.action,
            color: getComputedStyle(button).color,
          })),
      }
    })
    const normalActionColors = new Map()
    for (const action of colors.normal) {
      expect(action.action, `${item.route} 的普通操作缺少 data-action`).toBeTruthy()
      if (normalActionColors.has(action.action)) {
        expect(action.color, `${item.route} 的同类操作颜色不稳定`)
          .toBe(normalActionColors.get(action.action))
      } else {
        normalActionColors.set(action.action, action.color)
      }
    }
    expect(new Set(normalActionColors.values()).size, `${item.route} 的不同操作颜色未区分`)
      .toBe(normalActionColors.size)
    expect(new Set(colors.danger.map(action => action.color)).size,
      `${item.route} 的危险操作颜色不稳定`).toBe(1)
    expect(colors.danger[0].color, `${item.route} 的危险操作未与普通操作区分`)
      .not.toBe(colors.normal[0].color)
  }

  await page.goto('http://tianshu.local/index.html#/rule/101')
  const lifecycleTab = page.getByRole('tab', { name: /生命周期/ })
  await expect(lifecycleTab).toBeVisible()
  await expect(lifecycleTab).toHaveAttribute('aria-selected', 'true')
  await expect(
    page.getByRole('button', { name: '进入设计', exact: true })
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: '发布前校验', exact: true })
  ).toBeVisible()
  const submitReview = page.getByRole('button', {
    name: '提交评审',
    exact: true,
  })
  await expect(submitReview).toBeVisible()
  await expect(submitReview).toHaveClass(/el-button--primary/)

  await page.goto('http://tianshu.local/index.html#/designer/cross/101')
  const deleteCrossRow = page.getByRole('button', {
    name: '删除此行',
    exact: true,
  })
  await expect(deleteCrossRow).toHaveClass(/el-button--danger/)
  const addCrossRow = page
    .locator('.add-row-trigger')
    .getByRole('button', { name: '添加行', exact: true })
  await expect(addCrossRow).toHaveClass(/el-button--primary/)

  await page.goto('http://tianshu.local/index.html#/model')
  const modelTypeContent = page
    .locator('.el-table__body .el-tag__content:visible')
    .filter({ hasText: 'NeuralNet（神经网络）' })
    .first()
  await expect(modelTypeContent).toBeVisible()
  const modelTypeClipped = await modelTypeContent.evaluate((content) => {
    const cell = content.closest('td')
    if (!cell) return true
    return (
      content.scrollWidth > content.clientWidth + 1 ||
      content.getBoundingClientRect().right > cell.getBoundingClientRect().right - 4
    )
  })
  expect(modelTypeClipped, '模型大类标签在桌面宽度下被截断').toBe(false)

  assertClean()
})
