const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const {
  createDesignerApiData,
} = require('./support/designerFixtures.cjs')
const { createDetailApiData } = require('./support/detailFixtures.cjs')

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
