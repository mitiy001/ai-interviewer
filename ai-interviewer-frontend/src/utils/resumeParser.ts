/**
 * 简历纯文本 → 结构化数据 智能解析（双栏布局版）
 *
 * 参考现实市场简历样式（超级简历/五百丁/Canva），将 PDF 解析出的纯文本
 * 识别为双栏结构：
 *   - 左栏：联系方式 / 教育背景 / 专业技能（分组标签）/ 证书 / 校园经历
 *   - 右栏：个人简介 / 工作经历 / 项目经历 / 自我评价
 *
 * 识别策略（启发式，覆盖 90% 中文简历格式）：
 *   1. 头部扫描：姓名 + 联系方式（邮箱/电话/GitHub/网站）
 *   2. 章节切分：按关键词识别 11 类常见章节
 *   3. 技能分组：识别「编程语言/框架/中间件」等子分组
 *   4. 时间线：识别「时间 | 公司 | 职位」三段式 + 描述行 + 技术栈行
 */

/** 联系方式项 */
export interface ContactItem {
  label: string
  value: string
  icon: string
}

/** 教育背景条目 */
export interface EduItem {
  school: string
  major: string
  period: string
  courses?: string
}

/** 技能分组 */
export interface SkillGroup {
  label: string
  tags: string[]
}

/** 时间线条目（工作/项目/教育经历） */
export interface TimelineItem {
  period: string
  title: string
  subtitle: string
  org?: string
  description: string[]
  techStack?: string
  metrics?: { num: string; lbl: string }[]
}

/** 左栏章节 */
export interface SidebarSection {
  title: string
  type: 'contact' | 'education' | 'skills' | 'certs' | 'campus' | 'text'
  contact?: ContactItem[]
  education?: EduItem[]
  skills?: SkillGroup[]
  certs?: string[]
  campus?: { role: string; desc: string }[]
  paragraphs?: string[]
}

/** 右栏章节 */
export interface MainSection {
  title: string
  en: string
  type: 'summary' | 'timeline' | 'eval'
  timeline?: TimelineItem[]
  summary?: string
  evalList?: string[]
}

/** 解析后的结构化简历（双栏） */
export interface ParsedResume {
  name: string
  jobTitle: string
  sidebar: SidebarSection[]
  main: MainSection[]
  rawText: string
}

// ===== 章节关键词映射 =====
// 左栏章节（type 对应 SidebarSection.type）
const SIDEBAR_SECTIONS: { regex: RegExp; title: string; type: SidebarSection['type'] }[] = [
  { regex: /^(联系方式|个人信息|联系信息|contact)\s*[:：]?$/i, title: '联系方式', type: 'contact' },
  { regex: /^(教育背景|教育经历|学历|education)\s*[:：]?$/i, title: '教育背景', type: 'education' },
  { regex: /^(专业技能|技能清单|技能特长|技术栈|skills?|technical\s*skills)\s*[:：]?$/i, title: '专业技能', type: 'skills' },
  { regex: /^(证书|资格证书|资质|certificates?|qualifications?)\s*[:：]?$/i, title: '证书', type: 'certs' },
  { regex: /^(校园经历|学生工作|社团经历|activities)\s*[:：]?$/i, title: '校园经历', type: 'campus' },
]

// 右栏章节（type 对应 MainSection.type）
const MAIN_SECTIONS: { regex: RegExp; title: string; en: string; type: MainSection['type'] }[] = [
  { regex: /^(求职意向|求职目标|个人简介|简介|objective|career\s*objective|profile|summary|about\s*me)\s*[:：]?$/i, title: '个人简介', en: 'Profile', type: 'summary' },
  { regex: /^(工作经历|工作经验|实习经历|实习经验|工作履历|employment|experience|work\s*experience)\s*[:：]?$/i, title: '工作经历', en: 'Experience', type: 'timeline' },
  { regex: /^(项目经历|项目经验|projects?|project\s*experience)\s*[:：]?$/i, title: '项目经历', en: 'Projects', type: 'timeline' },
  { regex: /^(自我评价|个人总结|自我描述|self\s*evaluation|about)\s*[:：]?$/i, title: '自我评价', en: 'About', type: 'eval' },
]

// 联系方式正则
const EMAIL_RE = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/
const PHONE_RE = /(?:1[3-9]\d{9})|(?:0\d{2,3}-?\d{7,8})/
const GITHUB_RE = /github\.com\/[a-zA-Z0-9_-]+/i

/** 时间段识别 */
const PERIOD_RE = /(\d{4}\s*[./年-]\s*\d{1,2}\s*[./月]?\s*[-~至到—–\s]+\s*(?:至今|现在|now|present|\d{4}\s*[./年-]\s*\d{1,2}\s*[./月]?))|(\d{4}\s*[./年-]\s*\d{1,2}\s*[./月]?\s*[-~至到—–\s]*\s*至今)/i

/** 技能子分组关键词 */
const SKILL_GROUP_KEYWORDS: { regex: RegExp; label: string }[] = [
  { regex: /编程语言|语言/, label: '编程语言' },
  { regex: /框架|开发框架|后端框架/, label: '开发框架' },
  { regex: /微服务/, label: '微服务' },
  { regex: /AI|大模型|人工智能/, label: 'AI 应用开发' },
  { regex: /数据|中间件|数据库/, label: '数据与中间件' },
  { regex: /操作系统|系统|linux/, label: '操作系统' },
  { regex: /前端|frontend|vue|react/, label: '前端' },
  { regex: /工具|devops|部署/, label: '工具与运维' },
]

/**
 * 解析纯文本简历为双栏结构化数据
 */
export function parseResumeToSections(text: string): ParsedResume {
  if (!text || !text.trim()) {
    return { name: '', jobTitle: '', sidebar: [], main: [], rawText: text || '' }
  }

  const lines = text.split(/\r?\n/).map((l) => l.trim())
  const nonEmpty = lines.filter((l) => l.length > 0)

  // 1. 提取姓名 + 职位
  let name = ''
  let jobTitle = ''
  let headerEndIdx = 0
  const isChineseName = (s: string) => /^[\u4e00-\u9fa5]{2,8}$/.test(s)
  const isEnglishName = (s: string) => /^[a-zA-Z\s]{2,30}$/.test(s) && !/\d/.test(s)
  const isLikelyName = (s: string) => isChineseName(s) || isEnglishName(s)
  for (let i = 0; i < Math.min(nonEmpty.length, 6); i++) {
    const line = nonEmpty[i]
    if (EMAIL_RE.test(line) || PHONE_RE.test(line) || line.includes('@')) continue
    if (isSectionTitle(line)) continue
    // 姓名通常 2-8 中文字符，或英文名
    if (!name && isLikelyName(line)) {
      name = line
      headerEndIdx = lines.indexOf(line) + 1
      // 下一行可能是职位
      if (i + 1 < nonEmpty.length) {
        const next = nonEmpty[i + 1]
        const jobRe = /工程师|开发|程序员|设计师|经理|主管|实习|analyst|engineer|developer/i
        if (jobRe.test(next) && !isSectionTitle(next)) {
          jobTitle = next
          headerEndIdx = lines.indexOf(next) + 1
        }
      }
      break
    }
  }
  if (!name && nonEmpty.length > 0) {
    name = nonEmpty[0].split(/[\s|｜,，]/)[0].slice(0, 20)
    headerEndIdx = lines.indexOf(nonEmpty[0]) + 1
  }

  // 2. 提取联系方式（扫描头部 15 行）
  const headerText = lines.slice(0, Math.min(headerEndIdx + 15, lines.length)).join('\n')
  const contactItems: ContactItem[] = []
  const emailMatch = headerText.match(EMAIL_RE)
  if (emailMatch) contactItems.push({ label: '邮箱', value: emailMatch[0], icon: 'mail' })
  const phoneMatch = headerText.match(PHONE_RE)
  if (phoneMatch) contactItems.push({ label: '电话', value: phoneMatch[0], icon: 'phone' })
  const githubMatch = headerText.match(GITHUB_RE)
  if (githubMatch) contactItems.push({ label: 'GitHub', value: githubMatch[0], icon: 'github' })

  // 3. 章节切分
  const sidebarSections: SidebarSection[] = []
  const mainSections: MainSection[] = []
  // 联系方式自动放左栏（即使没显式标题）
  if (contactItems.length > 0) {
    sidebarSections.push({ title: '联系方式', type: 'contact', contact: contactItems })
  }

  let currentTitle = ''
  let currentSidebarType: SidebarSection['type'] | null = null
  let currentMainType: MainSection['type'] | null = null
  let currentEn = ''
  let currentLines: string[] = []

  const flushSection = () => {
    const nonEmptyLines = currentLines.filter((l) => l.trim().length > 0)
    if (nonEmptyLines.length === 0) {
      currentTitle = ''
      currentSidebarType = null
      currentMainType = null
      currentLines = []
      return
    }

    if (currentSidebarType) {
      if (currentSidebarType === 'education') {
        sidebarSections.push({
          title: currentTitle,
          type: 'education',
          education: parseEducation(nonEmptyLines),
        })
      } else if (currentSidebarType === 'skills') {
        sidebarSections.push({
          title: currentTitle,
          type: 'skills',
          skills: parseSkillGroups(nonEmptyLines),
        })
      } else if (currentSidebarType === 'certs') {
        sidebarSections.push({
          title: currentTitle,
          type: 'certs',
          certs: nonEmptyLines,
        })
      } else if (currentSidebarType === 'campus') {
        sidebarSections.push({
          title: currentTitle,
          type: 'campus',
          campus: parseCampus(nonEmptyLines),
        })
      } else {
        sidebarSections.push({
          title: currentTitle,
          type: 'text',
          paragraphs: nonEmptyLines,
        })
      }
    } else if (currentMainType) {
      if (currentMainType === 'summary') {
        mainSections.push({
          title: currentTitle,
          en: currentEn,
          type: 'summary',
          summary: nonEmptyLines.join(' '),
        })
      } else if (currentMainType === 'timeline') {
        mainSections.push({
          title: currentTitle,
          en: currentEn,
          type: 'timeline',
          timeline: parseTimeline(nonEmptyLines),
        })
      } else if (currentMainType === 'eval') {
        mainSections.push({
          title: currentTitle,
          en: currentEn,
          type: 'eval',
          evalList: nonEmptyLines,
        })
      }
    }
    currentTitle = ''
    currentSidebarType = null
    currentMainType = null
    currentLines = []
  }

  for (let i = headerEndIdx; i < lines.length; i++) {
    const line = lines[i]
    if (!line) continue

    const sidebarMatch = matchSidebarTitle(line)
    const mainMatch = matchMainTitle(line)

    if (sidebarMatch) {
      flushSection()
      currentTitle = sidebarMatch.title
      currentSidebarType = sidebarMatch.type
      currentLines = []
    } else if (mainMatch) {
      flushSection()
      currentTitle = mainMatch.title
      currentEn = mainMatch.en
      currentMainType = mainMatch.type
      currentLines = []
    } else if (currentTitle) {
      currentLines.push(line)
    } else {
      // 标题前的内容，若像简介则放右栏
      currentLines.push(line)
      if (i === lines.length - 1 || isSectionTitle(lines[i + 1] || '')) {
        if (currentLines.length > 0) {
          mainSections.push({
            title: '个人简介',
            en: 'Profile',
            type: 'summary',
            summary: currentLines.join(' '),
          })
          currentLines = []
        }
      }
    }
  }
  flushSection()

  return {
    name,
    jobTitle,
    sidebar: sidebarSections,
    main: mainSections,
    rawText: text,
  }
}

function isSectionTitle(line: string): boolean {
  return matchSidebarTitle(line) !== null || matchMainTitle(line) !== null
}

function matchSidebarTitle(line: string): { title: string; type: SidebarSection['type'] } | null {
  for (const p of SIDEBAR_SECTIONS) {
    if (p.regex.test(line)) return { title: p.title, type: p.type }
  }
  return null
}

function matchMainTitle(line: string): { title: string; en: string; type: MainSection['type'] } | null {
  for (const p of MAIN_SECTIONS) {
    if (p.regex.test(line)) return { title: p.title, en: p.en, type: p.type }
  }
  return null
}

/** 解析教育背景：识别「学校 + 专业 + 时间 + 课程」 */
function parseEducation(lines: string[]): EduItem[] {
  const items: EduItem[] = []
  let current: EduItem | null = null

  for (const line of lines) {
    const periodMatch = line.match(PERIOD_RE)
    // 学校行：含「大学/学院/学校」且不含时间
    if (/大学|学院|学校|university|college/i.test(line) && !periodMatch) {
      if (current) items.push(current)
      current = { school: line, major: '', period: '' }
    } else if (periodMatch && current) {
      current.period = periodMatch[0].trim()
    } else if (/专业|学位|本科|硕士|博士|major|bachelor|master/i.test(line) && current) {
      current.major = line
    } else if (/主修|课程|courses/i.test(line) && current) {
      current.courses = line.replace(/^.*[:：]\s*/, '')
    } else if (current) {
      // 追加到课程或专业
      if (!current.major) {
        current.major = line
      } else if (!current.courses) {
        current.courses = line
      }
    } else {
      // 无学校前缀，直接作为独立条目
      if (current) items.push(current)
      current = { school: line, major: '', period: '' }
    }
  }
  if (current) items.push(current)
  return items
}

/** 解析技能分组：识别「编程语言: Java, Python」格式 */
function parseSkillGroups(lines: string[]): SkillGroup[] {
  const groups: SkillGroup[] = []

  for (const line of lines) {
    // 识别「分组名: 技能列表」格式
    const groupMatch = line.match(/^([^:：]{2,12})\s*[:：]\s*(.+)$/)
    if (groupMatch) {
      const label = groupMatch[1].trim()
      const tagsStr = groupMatch[2].trim()
      const tags = tagsStr.split(/[,，、；;|｜\/\s]+/).map((s) => s.trim()).filter((s) => s.length > 0 && s.length <= 25)
      if (tags.length > 0) {
        groups.push({ label, tags })
        continue
      }
    }

    // 识别关键词分组
    const keywordGroup = SKILL_GROUP_KEYWORDS.find((g) => g.regex.test(line))
    if (keywordGroup) {
      const tagsStr = line.replace(/^.*[:：]\s*/, '')
      const tags = tagsStr.split(/[,，、；;|｜\/\s]+/).map((s) => s.trim()).filter((s) => s.length > 0 && s.length <= 25)
      if (tags.length > 0) {
        groups.push({ label: keywordGroup.label, tags })
        continue
      }
    }

    // 无分组：归入「其他技能」
    const tags = line.split(/[,，、；;|｜\/\s]+/).map((s) => s.trim()).filter((s) => s.length > 0 && s.length <= 25 && !/^\d+$/.test(s))
    if (tags.length > 0) {
      const lastGroup = groups[groups.length - 1]
      if (lastGroup && lastGroup.label === '其他技能') {
        lastGroup.tags.push(...tags)
      } else {
        groups.push({ label: '其他技能', tags })
      }
    }
  }
  return groups
}

/** 解析校园经历 */
function parseCampus(lines: string[]): { role: string; desc: string }[] {
  const items: { role: string; desc: string }[] = []
  let current: { role: string; desc: string } | null = null

  for (const line of lines) {
    if (/干事|部长|主席|member|president|部长/i.test(line) && !current) {
      current = { role: line, desc: '' }
    } else if (current) {
      current.desc = current.desc ? current.desc + ' ' + line : line
    } else {
      current = { role: line, desc: '' }
    }
  }
  if (current) items.push(current)
  return items
}

/** 解析时间线：识别「时间 | 标题 | 副标题 + 描述 + 技术栈」 */
function parseTimeline(lines: string[]): TimelineItem[] {
  const items: TimelineItem[] = []
  let current: TimelineItem | null = null

  /**
   * 识别"项目/工作分隔行"：以"项目"/"公司"/"工作"/"职位"开头，后接名称
   * 典型格式：
   *   - "项目 AI 面试官系统 时间"（时间段在下一行）
   *   - "项目: 医院体检报告解读平台"
   *   - "公司 阿里巴巴 时间"
   * 排除描述性内容："项目描述："、"个人职责："、"项目经历"（章节标题）
   */
  const isEntryHeaderLine = (line: string): { title: string } | null => {
    // 排除章节标题（由上层 matchMainTitle 处理）和描述/职责标签
    if (/项目描述|个人职责|项目经历|项目经验|工作经历|工作经验|实习经历/.test(line)) return null
    // 匹配 "项目 {名称}" / "公司 {名称}" / "职位 {名称}"，可有可无冒号
    // 名称至少 2 个字符，避免误匹配
    const m = line.match(/^(?:项目|公司|工作|职位)\s*[:：]?\s*(.{2,})/)
    if (m) {
      let title = m[1].trim()
      // 去除行尾的"时间"标签（PDF 解析常把"时间"作为列标签残留）
      title = title.replace(/\s*时间\s*$/, '').trim()
      if (title.length >= 2) return { title }
    }
    return null
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const periodMatch = line.match(PERIOD_RE)
    if (periodMatch) {
      if (current) items.push(current)
      const period = periodMatch[0].trim()
      const rest = line.replace(periodMatch[0], '').trim()
      const parts = rest.split(/[|｜,,]/).map((s) => s.trim()).filter((s) => s.length > 0)
      current = {
        period,
        title: parts[0] || '',
        subtitle: parts.slice(1).join(' · ') || '',
        description: [] as string[],
      }
      continue
    }

    // 项目/工作分隔行：时间段在下一行的情况
    const entryHeader = isEntryHeaderLine(line)
    if (entryHeader) {
      if (current) items.push(current)
      // 尝试从下一行提取时间段
      let period = ''
      if (i + 1 < lines.length) {
        const nextPeriod = lines[i + 1].match(PERIOD_RE)
        if (nextPeriod) {
          period = nextPeriod[0].trim()
          i++ // 消费下一行，避免重复处理
        }
      }
      current = {
        period,
        title: entryHeader.title,
        subtitle: '',
        description: [] as string[],
      }
      continue
    }

    // 技术栈行识别
    if (/技术栈|技术选型|tech\s*stack/i.test(line) && current) {
      current.techStack = line.replace(/^.*[:：]\s*/, '')
      continue
    }

    // 角色/职位行识别（作为 subtitle，不混入 description）
    if (current && !current.subtitle && /^(角色|职位|岗位|职务)\s*[:：]?\s*(.{2,})/.test(line)) {
      const roleMatch = line.match(/^(?:角色|职位|岗位|职务)\s*[:：]?\s*(.{2,})/)
      if (roleMatch) {
        current.subtitle = roleMatch[1].trim()
        continue
      }
    }

    // 指标识别：数字+百分比/倍数
    const metricMatch = line.match(/(\d+(?:\.\d+)?%|\d+(?:\.\d+)?[倍xX]|↑\d+%|↓\d+%|\d+min\s*[→-]\s*\d+s|\d+\s*[→-]\s*\d+)/)
    if (metricMatch && current) {
      if (!current.metrics) current.metrics = []
      current.metrics.push({ num: metricMatch[0], lbl: line.replace(metricMatch[0], '').replace(/[:：，,]/g, '').trim() || '指标' })
      continue
    }

    if (current) {
      current.description.push(line)
    } else {
      // 无时间前缀的条目
      const parts = line.split(/[|｜,,]/).map((s) => s.trim()).filter((s) => s.length > 0)
      current = {
        period: '',
        title: parts[0] || line,
        subtitle: parts.length > 1 ? parts.slice(1).join(' · ') : '',
        description: [] as string[],
      }
    }
  }
  if (current) items.push(current)
  return items
}
