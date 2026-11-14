<!-- 我的种植：盆栽卡片 + 详情抽屉（浇水/阶段/时间线） -->
<template>
  <div>
    <!-- 顶部欢迎（实拍玉米田全景背景图 /banner/banner-corn.jpg 无印宽幅版 + 暖绿纯色遮罩，无渐变） -->
    <div class="banner">
      <img class="banner-img" src="/banner/banner-corn.jpg" alt="" />
      <div class="banner-mask"></div>
      <div class="banner-content">
        <div class="banner-title">🌱 {{ userStore.user?.realName || '欢迎' }}，欢迎回到智慧农场</div>
        <div class="banner-sub">今日 {{ todayTasks.length }} 项农事安排 · {{ overdueCount }} 项待处理 · 共养护 {{ plants.length }} 株植物</div>
      </div>
    </div>

    <!-- 顶部统计（白色小卡：与下方天气卡同款白卡+绿调柔影，hover 上浮扩散；四卡均可点击弹明细） -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <el-card shadow="hover" class="stat-card" :class="{ clickable: c.action }" @click="openStat(c.action)">
          <div class="num" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="label">{{ c.label }}<span v-if="c.action" class="stat-tip"> 点击看明细</span></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 我的种植物明细弹窗（行点击打开抽屉详情） -->
    <el-dialog v-model="plantsDialog" title="🌿 我的种植物明细" width="620px" append-to-body>
      <el-empty v-if="!plants.length" description="还没有种植物，点底部「添加种植物」开始 🌱" :image-size="60" />
      <template v-else>
        <div v-for="p in plants" :key="p.id" class="stat-item" @click="openDetail(p); plantsDialog = false">
          <div class="h-left">
            <div class="h-name">
              <span>{{ p.speciesEmoji }} {{ p.name }}</span>
              <el-tag :type="health(p).type" size="small">{{ health(p).text }}</el-tag>
              <el-tag v-if="p.stage?.stageName" size="small" type="info">{{ p.stage.stageName }}</el-tag>
            </div>
            <div class="h-desc">{{ p.speciesName }} · 种了 {{ days(p) }} 天 · {{ p.deviceName || '未绑定设备' }}</div>
          </div>
          <el-button link type="primary">查看详情 →</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 设备明细弹窗（在线/离线全列，可跳监控页） -->
    <el-dialog v-model="devicesDialog" title="📡 设备明细" width="620px" append-to-body>
      <el-empty v-if="!devices.length" description="暂无设备，添加种植物时绑定传感器设备 📡" :image-size="60" />
      <template v-else>
        <div v-for="d in devices" :key="d.id" class="stat-item">
          <div class="h-left">
            <div class="h-name">
              <span>📡 {{ d.deviceName }}</span>
              <el-tag size="small" :type="d.status === 'online' ? 'success' : 'info'">
                {{ d.status === 'online' ? '在线' : '离线' }}
              </el-tag>
            </div>
            <div class="h-desc">设备编号 {{ d.deviceNo }} · 绑定植物：{{ devPlants(d) }} · 最近在线 {{ fmtTime(d.lastOnlineTime) }}</div>
          </div>
          <el-button link type="primary" @click="router.push('/monitor')">去监控 →</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 待确认建议明细弹窗（可直接确认处理） -->
    <el-dialog v-model="advicesDialog" title="🤖 待确认建议明细" width="620px" append-to-body>
      <el-empty v-if="!pendingAdvices.length" description="暂无待确认建议，一切正常 🎉" :image-size="60" />
      <template v-else>
        <div v-for="a in pendingAdvices" :key="a.id" class="stat-item">
          <div class="h-left">
            <div class="h-name">
              <el-tag size="small" :type="adviceTag(a.type)">{{ adviceName(a.type) }}</el-tag>
              <span>{{ a.title }}</span>
            </div>
            <div class="h-desc">{{ a.content }}</div>
            <!-- 图标按「这盆植物所属品种」的 emoji 显示（后端 fillPlant 已填 plantEmoji），不再写死 🌿 -->
            <div class="h-desc">{{ fmtTime(a.remindTime) }}<span v-if="a.plantName"> · {{ a.plantEmoji || '🪴' }} {{ a.plantName }}</span></div>
          </div>
          <el-button size="small" type="primary" plain @click="ack(a)">确认处理</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 健康关注明细弹窗 -->
    <el-dialog v-model="healthDialog" title="🔍 健康关注明细" width="620px" append-to-body>
      <el-empty v-if="!healthList.length" description="暂无健康关注，所有植物状态良好 🎉" :image-size="60" />
      <template v-else>
        <div v-for="(item, i) in healthList" :key="i" class="stat-item" @click="openDetail(item.plant); healthDialog = false">
          <div class="h-left">
            <div class="h-name">
              <span>{{ item.plant.speciesEmoji }} {{ item.plant.name }}</span>
              <el-tag :type="item.tag.type" size="small">{{ item.tag.text }}</el-tag>
            </div>
            <div class="h-desc">{{ item.desc }}</div>
          </div>
          <el-button link type="primary">查看详情 →</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 当前城市天气卡片（按用户所在区域，个人中心改城市后即时生效） -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="hover">
          <div class="weather-head">
            <span class="weather-title">⛅ {{ weather.cityName }} · 未来 3 天天气</span>
            <el-button size="small" type="primary" link @click="loadWeather">刷新</el-button>
          </div>
          <el-empty v-if="!weather.days?.length" description="暂未获取到天气数据" :image-size="60" />
          <div v-else class="weather-days">
            <div v-for="(d, i) in weather.days" :key="d.date" class="weather-day" :class="{ today: i === 0 }">
              <div class="w-date">{{ i === 0 ? '今天' : i === 1 ? '明天' : '后天' }}<span class="w-sub">{{ d.date.slice(5) }}</span></div>
              <div class="w-icon">{{ iconOf(d.text) }}</div>
              <div class="w-text">{{ d.text }}</div>
              <div class="w-temp">{{ d.tempMax }}℃ / {{ d.tempMin }}℃</div>
              <div class="w-wind">风力 {{ d.wind }} 级</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 今日农事清单 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="hover">
          <div class="today-head">
            <div class="today-title">📋 今日农事清单
              <span class="today-date">{{ dateText }}</span>
              <el-tag v-if="overdueCount" type="danger" size="small" style="margin-left: 8px">{{ overdueCount }} 项已逾期</el-tag>
            </div>
            <div class="today-right">
              <span class="today-stat">待办 <b>{{ todayTasks.filter((t) => t.status === 'pending').length }}</b></span>
              <span class="today-stat done">已完成 <b>{{ todayTasks.filter((t) => t.status === 'done').length }}</b></span>
              <el-button size="small" type="primary" link @click="loadToday">刷新</el-button>
            </div>
          </div>
          <el-empty v-if="!todayTasks.length" description="今天没有农事安排，好好休息 🌿" :image-size="60" />
          <div v-else class="today-list">
            <div v-for="t in todayTasks" :key="t.id + '-' + listVersion" class="today-item" :class="{ done: t.status === 'done' }">
              <el-checkbox :model-value="t.status === 'done'" :disabled="t.status === 'done'" title="已完成不可取消，补充记录请点「记录」" @change="() => toggleTask(t)" />
              <span class="t-time" :class="{ overdue: overdue(t) }">{{ t.remindTime.slice(11, 16) }}</span>
              <span class="t-title">{{ t.title }}</span>
              <el-tag size="small" :type="taskTag(t).type">{{ taskTag(t).text }}</el-tag>
              <el-tag v-if="t.plantName" size="small" type="success" class="plant-tag">{{ t.plantEmoji }} {{ t.plantName }}</el-tag>
              <el-button v-if="t.status === 'done'" size="small" type="primary" link @click="openExec(t)">记录</el-button>
              <span v-if="overdue(t)" class="t-overdue">已逾期</span>
              <span v-if="t.content" class="t-content">{{ t.content }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 盆栽卡片墙 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="6" v-for="p in plants" :key="p.id">
        <el-card shadow="hover" class="plant-card" @click="openDetail(p)">
          <div class="plant-photo">
            <el-image v-if="p.photo" :src="p.photo" fit="cover" class="photo-img" />
            <div v-else class="photo-emoji">{{ p.speciesEmoji || '🪴' }}</div>
            <el-tag :type="health(p).type" class="health-tag">{{ health(p).text }}</el-tag>
          </div>
          <div class="plant-body">
            <div class="plant-name">{{ p.name }}</div>
            <div class="plant-species">{{ p.speciesName }} · 种了 {{ days(p) }} 天
              <span v-if="p.stage?.stageName" class="stage-tag" :class="p.stage.overdue ? 'stage-overdue' : ''">{{ p.stage.stageName }}</span>
            </div>
            <div class="plant-metrics">
              <div class="pm">
                <span v-if="plantOffline(p)" class="pm-val off">--</span>
                <span v-else class="pm-val" :class="{ warn: warn(p, 'soil') }">{{ fmt(p.latest?.soil_humidity, '%') }}</span>
                <span class="pm-lbl">土壤湿度</span>
              </div>
              <div class="pm">
                <span v-if="plantOffline(p)" class="pm-val off">--</span>
                <span v-else class="pm-val" :class="{ warn: warn(p, 'temp') }">{{ fmt(p.latest?.air_temp, '℃') }}</span>
                <span class="pm-lbl">气温</span>
              </div>
              <div class="pm">
                <span v-if="plantOffline(p)" class="pm-val off">--</span>
                <span v-else class="pm-val">{{ fmt(p.latest?.air_humidity, '%') }}</span>
                <span class="pm-lbl">空气湿度</span>
              </div>
              <div class="pm">
                <span v-if="plantOffline(p)" class="pm-val off">--</span>
                <span v-else class="pm-val">{{ p.latest?.light ? Math.round(Number(p.latest.light)).toLocaleString() : '—' }}</span>
                <span class="pm-lbl">光照 lux</span>
              </div>
            </div>
            <div class="plant-foot">
              <span class="pf-dev">{{ p.deviceName || '未绑定设备' }}</span>
              <span>
                <el-button size="small" type="primary" link @click.stop="openEdit(p)">编辑</el-button>
                <el-button size="small" type="danger" link @click.stop="del(p)">删除</el-button>
              </span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="plant-card add-card" @click="openEdit()">
          <el-icon :size="34" color="#9aa59c"><Plus /></el-icon>
          <span>添加种植物</span>
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer" :title="detail?.name || '种植物详情'" size="720px" append-to-body>
      <template v-if="detail">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-image v-if="detail.photo" :src="detail.photo" fit="cover" class="drawer-photo" />
            <div v-else class="drawer-emoji">{{ detail.speciesEmoji || '🪴' }}</div>
            <div class="info-line"><b>品种：</b>{{ detail.speciesEmoji }} {{ detail.speciesName }}</div>
            <div class="info-line"><b>绑定设备：</b>{{ detail.deviceName || '未绑定' }}</div>
            <div class="info-line"><b>种植日期：</b>{{ detail.plantDate || '—' }}</div>
            <div class="info-line"><b>种植环境：</b>{{ detail.location || '—' }}</div>
            <div class="info-line"><b>种植方式：</b>{{ detail.growMethod || '—' }}</div>
            <div class="info-line"><b>光照时长：</b>{{ detail.sunlight || '—' }}</div>
            <div class="info-line" v-if="detail.location !== '菜地'"><b>容器：</b>{{ detail.container || '—' }}</div>
            <!-- 读 detail.stage.stageName（后端装配过的「当前阶段」），不是 detail.stageName（那是库里存的标注起点列）。 -->
            <div class="info-line"><b>生长阶段：</b>{{ detail.stage?.stageIndex >= 0 ? detail.stage.stageName : (detail.stageName || '—') }}</div>
            <div class="info-line" v-if="detail.remark"><b>备注：</b>{{ detail.remark }}</div>
            <el-divider />
            <!-- 浇水时机 -->
            <div v-if="detail.watering" class="watering-bar" :class="'w-' + detail.watering.status">
              <span class="w-label">{{ detail.watering.label }}</span>
              <span class="w-msg">{{ detail.watering.message }}</span>
            </div>
            <!-- 生长周期（推算/手动标注同构展示；手动标注以 stage_date 为阶段第 1 天）【进度条】 -->
            <div v-if="detail.stage?.stageName && detail.stage.stageIndex >= 0" class="stage-block">
              <div class="sb-title">🌱 生长周期 · {{ detail.stage.stageName }}
                <span class="stage-day" v-if="detail.stage.stageTotal < 9999">第 {{ detail.stage.stageDay }}/{{ detail.stage.stageTotal }} 天</span>
                <span class="stage-day" v-else>第 {{ detail.stage.stageDay }} 天</span>
              </div>
              <el-progress v-if="detail.stage.stageTotal < 9999" :percentage="stagePct(detail.stage)" :stroke-width="14"
                :format="() => detail.stage.stageName" color="#3f7d4c" />
              <div v-if="detail.stage.desc" class="stage-desc">{{ detail.stage.desc }}</div>
              <div v-if="detail.stage.nextStage" class="stage-next">下一阶段：{{ detail.stage.nextStage }}</div>
              <!-- 超期提示：只有"整个生长周期都走完了"才会出现（中途阶段由后端自动顺延，不会停）。 -->
              <div v-if="detail.stage.overdue" class="stage-overdue-tip">
                ⚠️ 已走完「{{ detail.stage.stageName }}」的全部 {{ detail.stage.stageTotal }} 天，整个生长周期已结束 —— {{ overdueAdvice(detail.stage, detail.species) }}
              </div>
            </div>
            <!-- 品种适宜环境：把品种库里的对照参数（湿度/气温/光照/浇水施肥周期）摊给农户看 -->
            <div class="species-box">
              <div class="sb-title">🌱 {{ detail.speciesName }} 适宜环境</div>
              <div class="info-line">土壤湿度：{{ detail.species?.soilMin }}% ~ {{ detail.species?.soilMax }}%</div>
              <div class="info-line">气温：{{ detail.species?.tempMin }}℃ ~ {{ detail.species?.tempMax }}℃</div>
              <div class="info-line">光照需求：{{ detail.species?.lightLevel }}</div>
              <div class="info-line">浇水周期：约每 {{ detail.species?.waterCycle }} 天</div>
              <div class="info-line">施肥周期：约每 {{ detail.species?.fertilizeCycle }} 天</div>
              <div class="info-line tips">{{ detail.species?.careTips }}</div>
              <div class="info-line diseases" v-if="detail.species?.diseases">
                ⚠️ 易发病虫害：{{ detail.species.diseases }}
                <el-link type="primary" @click="router.push('/diseases')">去查防治方法 →</el-link>
              </div>
              <div class="info-line" style="display: flex; align-items: center; gap: 10px">
                <el-button size="small" type="primary" plain @click="genCarePlan">🪴 生成养护计划</el-button>
                <span style="font-size: 12px; color: #909399">按品种的浇水/施肥周期生成待办，直接进「今日农事清单」</span>
              </div>
            </div>
          </el-col>
          <el-col :span="16">
            <div class="chart-head">
              <span>实时趋势（最近 60 分钟）</span>
              <el-radio-group v-model="metric" size="small" @change="loadHistory">
                <el-radio-button v-for="m in metricList" :key="m.key" :value="m.key">{{ m.short }}</el-radio-button>
              </el-radio-group>
            </div>
            <div class="chart-slot">
              <div ref="chartEl" style="height: 220px"></div>
            </div>
            <el-divider />
            <div class="record-head">
              <div class="block-title">📋 任务与生长记录</div>
              <span>
                <el-button size="small" type="primary" plain @click="openGrow">登记生长记录</el-button>
                <el-button size="small" type="primary" link @click="loadDone">刷新</el-button>
              </span>
            </div>
            <el-empty v-if="!doneRecords.length" description="暂无记录，完成任务或登记生长状况后这里会累积" :image-size="60" />
            <DoneRecords v-else :records="doneRecords" @delete="delRecord" @view="openDone" />
            <el-divider />
            <!-- 系统智能推送：这株植物收到的种植建议 / 虫害预防，可在这里直接确认处理 -->
            <div class="advice-head">
              <span>🤖 系统智能推送</span>
              <el-button size="small" type="primary" plain @click="loadPlantAdvice">刷新</el-button>
            </div>
            <el-empty v-if="!drawerAdvices.length" description="暂无建议，一切正常 🌿" :image-size="60" />
            <div v-for="a in drawerAdvices" :key="a.id" class="advice-item">
              <div class="advice-head-row">
                <el-tag size="small" :type="adviceTag(a.type)">{{ adviceName(a.type) }}</el-tag>
                <span class="advice-time">{{ a.remindTime.slice(5, 16) }}</span>
              </div>
              <div class="advice-title">{{ a.title }}</div>
              <div class="advice-content">{{ a.content }}</div>
              <el-button v-if="a.status === 'pending'" size="small" type="primary" plain @click="ack(a)">确认处理</el-button>
              <el-tag v-else size="small" :type="a.status === 'done' ? 'success' : 'info'" style="margin-top: 6px">
                {{ a.status === 'done' ? '已处理' : '已自动解除' }}
              </el-tag>
            </div>
          </el-col>
        </el-row>
      </template>
    </el-drawer>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑种植物' : '添加种植物'" width="520px" append-to-body>
      <el-form :model="form" label-width="90px">
        <el-form-item label="种植物名称" required>
          <el-input v-model="form.name" placeholder="如：一号田的番茄" />
        </el-form-item>
        <el-form-item label="品种" required>
          <el-select v-model="form.speciesId" placeholder="选择品种" style="width: 100%" filterable>
            <el-option v-for="s in species" :key="s.id" :value="s.id" :label="`${s.emoji} ${s.name}（土壤${s.soilMin}-${s.soilMax}% · ${s.tempMin}-${s.tempMax}℃）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="照片">
          <el-upload :show-file-list="false" :before-upload="beforeUpload">
            <div class="upload-box">
              <img v-if="form.photo" :src="form.photo" class="upload-img" />
              <div v-else class="upload-empty"><el-icon :size="20"><Plus /></el-icon> 上传照片</div>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="种植日期">
          <el-date-picker v-model="form.plantDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="绑定设备">
          <el-select v-model="form.deviceId" placeholder="选择我的传感器设备" clearable style="width: 100%">
            <el-option v-for="d in devices" :key="d.id" :value="d.id" :label="`${d.deviceName}（${d.deviceNo}）`" />
          </el-select>
          <div class="dev-tip">💡 一套设备可绑多种植物：空气温湿度/光照为环境共享，土壤湿度为插探头那一株的数据</div>
        </el-form-item>
        <el-form-item label="种植环境">
          <el-select v-model="form.location" placeholder="选择种植环境" clearable style="width: 100%">
            <el-option v-for="o in LOCATIONS" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="种植方式">
          <el-select v-model="form.growMethod" placeholder="选择种植方式" clearable style="width: 100%">
            <el-option v-for="o in METHODS" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="光照时长">
          <el-select v-model="form.sunlight" placeholder="选择光照时长" clearable style="width: 100%">
            <el-option v-for="o in SUNLIGHTS" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="needContainer" label="容器">
          <el-select v-model="form.container" placeholder="选择容器" clearable style="width: 100%">
            <el-option v-for="o in CONTAINERS" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="生长阶段">
          <el-select v-model="form.stageName" placeholder="选择生长阶段" clearable style="width: 100%">
            <el-option v-for="o in stageOpts" :key="o" :label="o" :value="o" />
          </el-select>
          <div class="dev-tip">💡 选了阶段就以「今天」作为该阶段第 1 天重新起算，走完这段天数后自动进入下一阶段；留空则完全按种下日期推算</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加种植物：4 步向导（新增走这个，编辑走上面的弹窗） -->
    <AddPlantWizard v-model="wizardOpen" :species="species" :devices="devices" @saved="loadAll" />

    <el-dialog v-model="growDialog" title="登记生长记录" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="记录时间">
          <el-date-picker v-model="growForm.recordTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="默认当前时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="株高(cm)">
          <el-input-number v-model="growForm.heightCm" :min="0" :max="999" :precision="1" placeholder="选填" style="width: 100%" />
        </el-form-item>
        <el-form-item label="叶色">
          <el-select v-model="growForm.leafColor" placeholder="选填" clearable style="width: 100%">
            <el-option v-for="c in ['嫩绿', '正常绿', '发黄', '干枯']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="长势">
          <el-select v-model="growForm.vigor" placeholder="选填" clearable style="width: 100%">
            <el-option v-for="v in ['旺盛', '正常', '缓慢', '停滞']" :key="v" :label="v" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="照片">
          <el-upload :show-file-list="false" :before-upload="beforeGrowUpload">
            <div class="upload-box">
              <img v-if="growForm.photo" :src="growForm.photo" class="upload-img" />
              <div v-else class="upload-empty"><el-icon :size="20"><Plus /></el-icon> 上传照片</div>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="growForm.remark" type="textarea" :rows="2" maxlength="500" placeholder="选填，如：长势观察、养护动作" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="growDialog = false">取消</el-button>
        <el-button type="primary" @click="saveGrow">保存</el-button>
      </template>
    </el-dialog>

    <!-- 农事任务执行记录（今日清单打勾/回看共用） -->
    <ExecutionDialog v-model="execDialog" :reminder="execReminder" @skip="finishFromDialog(false)" @recorded="finishFromDialog(true)" />
  </div>
</template>

<script setup lang="ts">
// 我的种植：用户端首页，农户登录后看到的第一个页面，也是整个系统的总控台。
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '../api'
import { useUserStore } from '../stores/user'
import ExecutionDialog from '../components/ExecutionDialog.vue'
import AddPlantWizard from '../components/AddPlantWizard.vue'
import DoneRecords from '../components/DoneRecords.vue'

// router 用于跳转到监控页/病虫害页；userStore 用于在欢迎语里显示农户昵称
const router = useRouter()
const userStore = useUserStore()

// 详情抽屉里实时曲线可切换的 6 项指标：key 对应后端字段名，short 是显示名，unit 单位，color 曲线颜色
const metricList = [
  { key: 'soil_humidity', short: '土壤湿度', unit: '%', color: '#3f7d4c' },
  { key: 'soil_temp', short: '土温', unit: '℃', color: '#52ae5e' },
  { key: 'air_temp', short: '气温', unit: '℃', color: '#ff6b6b' },
  { key: 'air_humidity', short: '空气湿度', unit: '%', color: '#f5a623' },
  { key: 'light', short: '光照', unit: 'lux', color: '#6f95a3' },
  { key: 'rssi', short: '信号强度', unit: 'dBm', color: '#979e8c' },
]

// 我的种植物列表：后端返回时已带上最新传感器数据 latest、品种参数 species、生长阶段 stage
const plants = ref<any[]>([])
// 品种库列表：品种下拉和生长阶段选项都用它
const species = ref<any[]>([])
// 我名下的传感器设备列表（绑定植物时选择）
const devices = ref<any[]>([])
// 系统推送的种植建议 / 虫害预防，统计卡「待确认建议」就是按它算的
const advices = ref<any[]>([])
/** 抽屉内系统推送：按植物精确查询（plant_advice/plant_pest/growth），不再标题前缀过滤 */
const drawerAdvices = ref<any[]>([])
/** 抽屉内记录时间线：任务执行记录 + 生长记录合并 */
const doneRecords = ref<any[]>([])
// 当前在抽屉里查看的那株植物
const detail = ref<any>(null)
// 详情抽屉的显隐
const drawer = ref(false)
// 编辑种植物弹窗的显隐（新增走 4 步向导，不用这个弹窗）
const dialog = ref(false)
// 「添加种植物」4 步向导的显隐
const wizardOpen = ref(false)
// 执行记录弹窗的显隐（子组件 ExecutionDialog）
const execDialog = ref(false)
// 当前要完成或回看的那条农事任务
const execReminder = ref<any>(null)
// 今日清单的「重建计数器」：执行记录弹窗一关就 +1，强制列表（连同勾选框）按数据库真实状态重建。
// 为什么需要它（2026-10-16 用户实测抓出）：el-checkbox 会记住自己被点过的视觉状态 ——
// 弹窗要是被右上角 × 或 ESC 关掉，任务其实**还是待办**，勾选框却停在"看起来已完成"；
// 下一次点击就会发出"取消勾选"事件，于是弹出"已完成不可取消"的错提示（现象：每隔一次就错一次）。
const listVersion = ref(0)
watch(execDialog, (open) => {
  if (!open) {
    listVersion.value++
    loadToday()
  }
})
// 抽屉曲线当前看的是哪项指标（默认土壤湿度）
const metric = ref('soil_humidity')
// 编辑种植物的表单（带 id 表示编辑已有植物）
const form = reactive<any>({})
// 与添加向导 AddPlantWizard.vue 保持一致的枚举
const LOCATIONS = ['阳台', '窗台', '露台', '小院', '室内', '菜地']
const METHODS = ['土培', '水培']
const SUNLIGHTS = ['少于2小时', '2-4小时', '4-6小时', '6小时以上']
const CONTAINERS = ['小盆', '中盆', '大盆', '水培盒', '还没准备']
const DEFAULT_STAGES = ['种子育苗', '幼苗期', '生长期', '花期', '结果期']
// 与添加向导 AddPlantWizard.vue 一致：growthStages 后端返回 JSON 字符串，必须 parse
const stageOpts = computed(() => {
  const gs = species.value.find((x: any) => x.id === form.speciesId)?.growthStages
  let names: string[] = []
  if (gs) {
    try { names = JSON.parse(gs).map((x: any) => x.stage).filter(Boolean) } catch { names = [] }
  }
  const base = names.length ? names : DEFAULT_STAGES
  // 编辑旧数据时保留不在品种清单里的既有阶段值，避免下拉显示裸值
  if (form.stageName && !base.includes(form.stageName)) return [...base, form.stageName]
  return base
})
// 只有「有容器」的场景才问容器：菜地是露天大田，没有盆的概念（与 AddPlantWizard.vue 同一套口径）
const needContainer = computed(() => form.location !== '菜地')
// 环境改成「菜地」时立刻清空容器，避免库里留下「菜地 + 大盆」这种脏组合
watch(() => form.location, (v) => {
  if (v === '菜地') form.container = ''
})

// 「登记生长记录」弹窗的显隐
const growDialog = ref(false)
// 生长记录表单：株高 / 叶色 / 长势 / 照片 / 备注
const growForm = reactive<any>({})
// 抽屉里曲线挂载的 DOM 容器
const chartEl = ref<HTMLElement>()
// echarts 实例（刻意不用 ref 包，避免被 Vue 转成响应式对象带来额外开销）
let chart: echarts.ECharts | null = null
// 60 秒自动刷新的定时器句柄，离开页面时要清掉
let timer: any = null

// 四张统计卡的内容：种植物数量、在线设备数、待确认建议数、需要关注的植物数；
const cards = computed(() => [
  { label: '我的种植物', value: plants.value.length, color: '#3f7d4c', action: 'plants' },
  { label: '在线设备', value: devices.value.filter((d) => d.status === 'online').length, color: '#52ae5e', action: 'devices' },
  { label: '待确认建议', value: advices.value.filter((a) => a.status === 'pending').length, color: '#f5a623', action: 'advices' },
  { label: '健康关注', value: plants.value.filter((p) => health(p).type !== 'success').length, color: '#ff6b6b', action: 'health' },
])

// ==================== 统计卡明细（四卡均可点击弹对应明细） ====================
const plantsDialog = ref(false)
const devicesDialog = ref(false)
const advicesDialog = ref(false)
const healthDialog = ref(false)
// 点某张统计卡：按卡片带的 action 打开对应明细弹窗；健康关注卡没带 action，落到最后的健康弹窗
function openStat(action?: string) {
  if (action === 'plants') plantsDialog.value = true
  else if (action === 'devices') devicesDialog.value = true
  else if (action === 'advices') advicesDialog.value = true
  else healthDialog.value = true
}
/** 待确认建议：仅 pending 的行 */
const pendingAdvices = computed(() => advices.value.filter((a) => a.status === 'pending'))
/** 设备行：绑定植物名（多株顿号连接） */
function devPlants(d: any) {
  const list = plants.value.filter((p) => p.deviceId === d.id).map((p) => p.name)
  return list.length ? list.join('、') : '未绑定'
}
/** 时间格式化：兼容 T 分隔与空格分隔的 datetime 字符串 */
function fmtTime(s: any) {
  return s ? String(s).replace('T', ' ').slice(0, 16) : '—'
}
// 健康关注明细列表：把健康状态不是「良好」的植物挑出来，附上判定标签和具体原因
const healthList = computed(() =>
  plants.value.filter((p) => health(p).type !== 'success').map((p) => {
    const h = health(p)
    return { plant: p, tag: h, desc: healthReason(p) }
  }),
)
// 生成这条健康关注的文字说明：按「需要浇水 / 温度偏高 / 光照不足」三种情况分别给出对应的处理建议
function healthReason(p: any) {
  const sp = p.species
  const l = p.latest || {}
  const h = health(p)
  if (h.type === 'danger') {
    return `当前土壤湿度 ${fmtHealth(l.soil_humidity)}%，低于品种下限 ${sp.soilMin}%，建议浇透水后观察盆底排水`
  }
  if (h.type === 'warning' && Number(l.air_temp) > sp.tempMax) {
    return `当前气温 ${fmtHealth(l.air_temp)}℃，高于品种上限 ${sp.tempMax}℃，注意遮阴通风降温`
  }
  return `当前光照 ${Math.round(Number(l.light))} lux，低于 2000 lux，建议移到光照更充足的位置`
}
// 健康说明里显示的数值格式：没数据显示「—」，否则保留 1 位小数
function fmtHealth(v: any) {
  return v === undefined || v === null ? '—' : Number(v).toFixed(1)
}

// ==================== 当前城市天气卡片 ====================
const weather = ref<any>({ cityName: '', days: [] })

// 拉所在城市天气（GET /api/weather/my），页面自动刷新和点「刷新」按钮都会调它
async function loadWeather() {
  weather.value = await http.get('/weather/my')
}

// 按天气文字里的关键字挑一个对应的 emoji 显示（雷/雪/雨/阴/云/晴，都不匹配就用默认多云）
function iconOf(t: string) {
  if (t.includes('雷')) return '⛈️'
  if (t.includes('雪')) return '❄️'
  if (t.includes('雨')) return '🌧️'
  if (t.includes('阴')) return '☁️'
  if (t.includes('云')) return '⛅'
  if (t.includes('晴')) return '☀️'
  return '🌤️'
}

// ==================== 今日农事清单 ====================
const TASK_TYPES = ['custom']
// 今日农事清单里的任务（含重复任务落在今天的那一次），按提醒时间升序排列
const todayTasks = ref<any[]>([])

// 今日日期文案（如「9月26日 星期五」），直接用本地时间拼出来，不走国际化组件
const dateText = `${new Date().getMonth() + 1}月${new Date().getDate()}日 星期${'日一二三四五六'[new Date().getDay()]}`

// 今日清单里已经逾期的条数，显示在标题旁边的红色标签上
const overdueCount = computed(() => todayTasks.value.filter((t) => overdue(t)).length)

// 判断一个时间字符串所代表的日期是不是今天（年月日三项都比一遍）
function isToday(s: string) {
  const d = new Date(s)
  const n = new Date()
  return d.getFullYear() === n.getFullYear() && d.getMonth() === n.getMonth() && d.getDate() === n.getDate()
}

/** 把日期归零到当天 0 点（算「隔了几天」用，避免时分秒干扰） */
function dayStart(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

/** 任务是否属于今天：单次看日期；每天恒真；每周比星期几；每月比几号；每年比月+日；每 N 天比「距锚点多少天」 */
function isTodayTask(r: any) {   // 【今天该不该做】
  if (r.repeatType === 'daily') return true
  if (r.repeatType === 'weekly') return new Date(r.remindTime).getDay() === new Date().getDay()
  if (r.repeatType === 'monthly') return new Date(r.remindTime).getDate() === new Date().getDate()
  if (r.repeatType === 'yearly') {
    // 2026-10-04 修：原来落到最下面按「单次」比（要求年月日全等）⇒ 每年任务只在创建那天出现一次，
    const d = new Date(r.remindTime)
    const n = new Date()
    return d.getMonth() === n.getMonth() && d.getDate() === n.getDate()
  }
  if (r.repeatType === 'interval') {
    // 每 N 天（2026-10-04 新增）：remindTime 既是首次提醒时刻、也是锚点（第 0 天），
    const step = Number(r.intervalDays)
    if (!step || step <= 1) return true     // 没带天数或 N=1：当每天，别把任务卡死
    const days = Math.round((dayStart(new Date()).getTime() - dayStart(new Date(r.remindTime)).getTime()) / 86400000)
    return days >= 0 && days % step === 0
  }
  return isToday(r.remindTime)
}

/** 待办且已过提醒时间 = 逾期（done/未到期不算）；重复任务按当天时分比较 */
function overdue(t: any) {   // 【逾期是算的】
  if (t.status !== 'pending') return false
  if (t.repeatType && t.repeatType !== 'none') {
    const [h, m] = t.remindTime.slice(11, 16).split(':').map(Number)
    const now = new Date()
    return now.getHours() > h || (now.getHours() === h && now.getMinutes() >= m)
  }
  return new Date(t.remindTime).getTime() < Date.now()
}

// 任务类型的显示名和标签颜色（今日清单只放自己建的农事任务，所以这里只登记了 custom 一种）
function taskTag(t: any) {
  const map: any = {
    custom: { text: '农事任务', type: 'info' },
  }
  return map[t.type] || { text: t.type, type: 'info' }
}

/** 今日清单：今天的任务类提醒（含重复任务当天实例），按时间升序 */
async function loadToday() {   // 【今日清单】
  const d = await http.get('/reminders', { params: { page: 1, size: 200 } })
  todayTasks.value = d.records
    .filter((r: any) => TASK_TYPES.includes(r.type) && isTodayTask(r))
    .sort((a: any, b: any) => a.remindTime.localeCompare(b.remindTime))
}

/** 点今日清单的勾选框 → 打开执行记录弹窗（弹窗里可保存记录，也可跳过）。
 *  ⚠️ 判据是**数据库里的 t.status**，不是 checkbox 传回来的勾选值 —— 理由见上面 listVersion 的注释：
 *  勾选框的视觉状态可能和库里不一致。已完成的任务不可取消（后端 changeStatus 对 done→pending 直接 400）。 */
function toggleTask(t: any) {
  if (t.status === 'done') {
    ElMessage.warning('已完成的任务不可取消，补充记录请点「记录」')
    return
  }
  execReminder.value = t
  execDialog.value = true
}

/** 弹窗收尾（2026-10-16 修，与 Reminders.vue 的写法对齐）：
 *  弹窗有两个入口 —— ① 点待办任务的勾选框（要"完成"）；② 点**已完成**任务的「记录」按钮补记（**不该再动状态**）。
 *  以前不分入口，一律回调 finishTask ⇒ 对已完成任务再发一次 done 请求 ⇒ 后端 400
 *  「任务已完成，今日无需重复完成；补充记录请使用「记录」功能」，于是"已保存"和红字同时弹出来（用户实测）。 */
function finishFromDialog(recorded: boolean) {
  if (execReminder.value?.status === 'done') {
    // 补记：记录本身由弹窗自己保存，这里只刷新列表与记录时间线，不再改状态
    loadToday()
    if (detail.value?.id) loadDone()
    return
  }
  finishTask(recorded)
}

/** 弹窗关闭后统一完成（recorded=是否有执行记录） */
async function finishTask(recorded: boolean) {
  const t = execReminder.value
  await http.put(`/reminders/${t.id}/status`, null, { params: { status: 'done' } })
  t.status = 'done'
  ElMessage.success(recorded ? `已完成并记录「${t.title}」` : `已完成「${t.title}」`)
  loadToday()
  if (detail.value?.id) loadDone()
}

/** 回看执行记录（done 项） */
function openExec(t: any) {
  execReminder.value = t
  execDialog.value = true
}

/** 记录板块「查看」：打开该任务的执行记录回看（生长条目忽略） */
function openDone(h: any) {
  if (h.kind === 'grow') return
  execReminder.value = { id: h.reminderId, title: h.title, status: 'done', type: h.type }
  execDialog.value = true
}

// 卡片上指标数值的显示格式：没数据显示「—」，否则保留 1 位小数并接上单位
function fmt(v: any, unit: string) {
  if (v === undefined || v === null) return '—'
  return `${Number(v).toFixed(1)}${unit}`
}

/** 绑定设备离线：不显示旧数据（deviceStatus 为 null=未绑定设备，保持现状） */
function plantOffline(p: any) {
  return p.deviceStatus && p.deviceStatus !== 'online'
}

// 这株植物已经种了多少天：优先用后端按生长阶段算好的天数，
function days(p: any) {
  if (p.stage?.dayPast !== undefined && p.stage.dayPast >= 0) return p.stage.dayPast
  if (!p.plantDate) return '?'
  return Math.max(0, Math.floor((Date.now() - new Date(p.plantDate).getTime()) / 86400000))
}

/** 生长周期进度百分比（长期阶段返回 100） */
function stagePct(s: any): number {
  if (!s || !s.stageTotal || s.stageTotal >= 9999) return 100
  return Math.min(100, Math.round((s.stageDay / s.stageTotal) * 100))
}

/** 超期提示的尾句：同样是「整个生长周期走完了」，一茬作物和多年生植株的下一步不是一回事。 */
function overdueAdvice(s: any, sp: any): string {
  const oneShot = sp?.category === 'crop' && !(s?.stageName || '').includes('休眠')
  return oneShot ? '该采收了，请及时收获' : '建议按植株实际长势重新标注阶段'
}

/** 健康状态：按最新数据与品种适宜区间对比 */
function health(p: any) {
  const sp = p.species
  const l = p.latest || {}
  if (sp && l.soil_humidity !== undefined && Number(l.soil_humidity) < sp.soilMin) {
    return { text: '💧 需要浇水', type: 'danger' as const }
  }
  if (sp && l.air_temp !== undefined && Number(l.air_temp) > sp.tempMax) {
    return { text: '🌡️ 温度偏高', type: 'warning' as const }
  }
  if (sp && l.light !== undefined && Number(l.light) < 2000 && sp.lightLevel !== '耐阴') {
    return { text: '☀️ 光照不足', type: 'warning' as const }
  }
  return { text: '✅ 状态良好', type: 'success' as const }
}

// 判断某项指标是否超出该品种的适宜区间：土壤湿度低于下限、或气温高于上限时，卡片上这个数字标红
function warn(p: any, kind: 'soil' | 'temp') {
  const sp = p.species
  const l = p.latest || {}
  if (kind === 'soil') return sp && l.soil_humidity !== undefined && Number(l.soil_humidity) < sp.soilMin
  return sp && l.air_temp !== undefined && Number(l.air_temp) > sp.tempMax
}

// 首屏全量加载：并发拉六份数据（我的植物、品种库、设备、系统建议、今日任务、天气）。
async function loadAll() {
  await Promise.all([
    http.get('/plants').then((d) => (plants.value = d)),
    http.get('/plant-species').then((d) => (species.value = d)),
    http.get('/devices').then((d) => (devices.value = d)),
    loadAdvice(),
    loadToday(),
    loadWeather(),
  ])
}

// 拉系统推送的种植建议和虫害预防（从提醒列表里筛出 plant_advice / plant_pest 两类）
async function loadAdvice() {
  const d = await http.get('/reminders', { params: { page: 1, size: 50 } })
  advices.value = d.records.filter((r: any) => r.type === 'plant_advice' || r.type === 'plant_pest')
}

/** 抽屉内系统推送（按植物查） */
async function loadPlantAdvice() {
  if (!detail.value?.id) return
  drawerAdvices.value = await http.get(`/reminders/plant/${detail.value.id}`)
}

/** 抽屉内记录时间线：custom 任务执行记录 + 生长记录，按时间倒序合并 */
async function loadDone() {
  if (!detail.value?.id) return   // 【合成时间线】
  const [h, g] = await Promise.all([
    // 仅农事任务完成记录（custom）：种植建议/虫害预防等系统推送在下方「系统智能推送」板块展示
    http.get('/reminders/history', { params: { plantId: detail.value.id, type: 'custom', limit: 100 } }),
    http.get(`/plants/${detail.value.id}/records`),
  ])
  const grow = (g || []).map((r: any) => ({ kind: 'grow', time: r.recordTime, ...r }))
  doneRecords.value = [...(h || []), ...grow].sort((a: any, b: any) =>
    String(b.time || '').localeCompare(String(a.time || '')))
}

/** 推送类型名称/tag（含生长提醒） */
// 推送类型的中文名：种植建议 / 虫害预防 / 生长提醒
function adviceName(t: string) {
  return ({ plant_advice: '种植建议', plant_pest: '虫害预防', growth: '生长提醒' } as any)[t] || t
}
// 推送类型对应的标签颜色
function adviceTag(t: string) {
  return ({ plant_advice: 'primary', plant_pest: 'warning', growth: 'warning' } as any)[t] || 'primary'
}

// 拉抽屉里实时曲线的数据：这株植物没绑设备就清空图表；
async function loadHistory() {
  if (!detail.value?.deviceId) {
    if (chartEl.value) chart?.clear()
    return
  }   // 【曲线60分钟】
  const list = await http.get(`/devices/${detail.value.deviceId}/history`, {
    params: { metric: metric.value, minutes: 60 },
  })
  const m = metricList.find((x) => x.key === metric.value)!
  if (!chartEl.value) return
  chart = chart || echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: list.map((p: any) => p.collectTime.slice(11, 16)), boundaryGap: false },
    yAxis: { type: 'value', name: m.unit, scale: true },
    series: [{
      name: m.short, type: 'line', smooth: true, showSymbol: false,
      data: list.map((p: any) => Number(p.value)),
      lineStyle: { color: m.color, width: 2 },
      areaStyle: { color: m.color + '22' },
    }],
  })
}

// 点植物卡片：打开右侧详情抽屉，同时拉三份数据 —— 这株植物的系统推送、记录时间线、绑设备的实时曲线（【抽屉拉三样】）
function openDetail(p: any) {
  detail.value = p
  drawer.value = true
  loadPlantAdvice()
  loadDone()
  // 延迟 100 毫秒，等抽屉渲染出图表容器后再画曲线
  setTimeout(loadHistory, 100)
}

// 点「生成养护计划」：按这株的品种周期（浇水/施肥天数）生成重复的农事任务，
async function genCarePlan() {
  if (!detail.value) return
  const d = await http.post(`/plants/${detail.value.id}/care-plan`)
  const created = d?.created ?? 0
  const skipped = d?.skipped ?? 0
  ElMessage.success(created
    ? `已生成 ${created} 条养护待办${skipped ? `（已存在 ${skipped} 条，跳过）` : ''}`
    : '养护待办已存在，无需重复生成')
  loadToday()   // 首页「今日农事清单」立刻刷新，不用手动点刷新
}

// 点「登记生长记录」：先清空表单再打开弹窗（记录时间留空，后端会按当前时间处理）
function openGrow() {
  Object.keys(growForm).forEach((k) => delete growForm[k])
  delete growForm.recordTime
  growDialog.value = true
}

// 生长记录里的照片上传：先 POST /api/upload 拿到图片地址填进表单；
async function beforeGrowUpload(file: File) {
  // 只允许图片：这份后缀白名单必须和后端 UploadController.ALLOWED_EXT 保持一致。
  const IMG_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!IMG_EXT.includes(ext)) {
    ElMessage.error('只支持 jpg / jpeg / png / gif / webp / bmp 格式的图片')
    return false
  }
  const fd = new FormData()
  fd.append('file', file)
  const d = await http.post('/upload', fd)
  growForm.photo = d.url
  ElMessage.success('照片上传成功')
  return false
}

// 点「保存」保存生长记录：株高/叶色/长势/照片至少得填一项；
async function saveGrow() {
  if (!growForm.heightCm && !growForm.leafColor && !growForm.vigor && !growForm.photo) {
    ElMessage.warning('请至少填写一项参数或照片')
    return
  }
  await http.post(`/plants/${detail.value.id}/records`, growForm)
  ElMessage.success('已登记并自动评估')
  growDialog.value = false
  loadDone()
}

// 删除一条生长记录：先弹确认框，确认后 DELETE /api/records/{id}，再刷新时间线
async function delRecord(r: any) {
  await ElMessageBox.confirm('确定删除这条生长记录？', '提示')
  await http.delete(`/records/${r.id}`)
  ElMessage.success('已删除')
  loadDone()
}

// 点某条建议/推送上的「确认处理」：把这条提醒任务状态改为已完成，
async function ack(a: any) {
  await http.put(`/reminders/${a.id}/status`, null, { params: { status: 'done' } })
  ElMessage.success('已确认处理')
  loadAdvice()
  loadPlantAdvice()
}

// 两种入口：不带参数（点「添加种植物」占位卡）→ 打开 4 步向导；
function openEdit(p?: any) {
  if (!p) {
    // 新增 → 4 步引导向导（编辑保持旧弹窗）
    wizardOpen.value = true
    return
  }
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { ...p })
  dialog.value = true
}

// 编辑弹窗里上传植物照片：先 POST /api/upload 拿到图片地址填进表单，返回 false 阻止 el-upload 重复上传
async function beforeUpload(file: File) {
  // 只允许图片：这份后缀白名单必须和后端 UploadController.ALLOWED_EXT 保持一致。
  const IMG_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!IMG_EXT.includes(ext)) {
    ElMessage.error('只支持 jpg / jpeg / png / gif / webp / bmp 格式的图片')
    return false
  }
  const fd = new FormData()
  fd.append('file', file)
  const d = await http.post('/upload', fd)
  form.photo = d.url
  ElMessage.success('照片上传成功')
  return false
}

// 点编辑弹窗的「保存」：名称和品种必填；带 id 走 PUT 更新、否则走 POST 新增，成功后刷新全页数据
async function save() {
  if (!form.name || !form.speciesId) {
    ElMessage.warning('种植物名称和品种不能为空')
    return
  }
  // 菜地没有容器概念，提交前兜底清一次（配置里没选容器也不会把旧值带进库）
  if (!needContainer.value) form.container = ''
  if (form.id) await http.put('/plants', form)
  else await http.post('/plants', form)
  ElMessage.success('保存成功')
  dialog.value = false
  loadAll()
}

// 删除种植物：先弹确认框，确认后 DELETE /api/plants/{id}；
async function del(p: any) {
  await ElMessageBox.confirm(`确定删除种植物「${p.name}」？`, '提示')
  await http.delete(`/plants/${p.id}`)
  ElMessage.success('已删除')
  if (detail.value?.id === p.id) {
    drawer.value = false
    detail.value = null
  }
  loadAll()
}

// 进页面先全量加载一次；然后每 60_000 毫秒（60 秒）自动刷新一遍，
onMounted(async () => {
  await loadAll()   // 【60秒轮询】
  timer = setInterval(() => {
    loadAll()
    if (drawer.value) loadHistory()
    if (drawer.value) loadDone()
    if (drawer.value) loadPlantAdvice()
  }, 60_000)
  window.addEventListener('resize', () => chart?.resize())
})
// 离开页面：清掉 60 秒定时器、销毁图表实例，避免后台空跑占资源
onUnmounted(() => {
  clearInterval(timer)
  chart?.dispose()
})
</script>

<style scoped>
/* ===== 顶部统计卡：与下方天气卡同款白色圆角卡 + 绿调柔影（全局 el-card 已供白底/20px 圆角/阴影/hover 上浮）；本区只调内容布局与色调 ===== */
.stat-card { text-align: center; }
.stat-card :deep(.el-card__body) { padding: 18px 10px 14px; }
.stat-card.clickable { cursor: pointer; }
.stat-card.clickable:hover .num { color: var(--neu-accent); }
.stat-card.clickable:hover .label { color: var(--neu-accent); }
.stat-card.clickable:hover .stat-tip { color: var(--accent-deep); }
.stat-card .num { font-size: 32px; font-weight: 700; line-height: 1.2; }
.stat-card .label { margin-top: 6px; color: var(--neu-ink2); font-size: 14px; }
.stat-tip { color: #3f7d4c; font-size: 12px; }

/* ===== 顶部欢迎横幅：实拍玉米田背景图 + 暖绿纯色遮罩（无渐变），保留凸起圆角卡形态 ===== */
.banner {
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-card);
}
.banner-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.banner-mask {
  position: absolute;
  inset: 0;
  background: rgba(50, 63, 43, 0.55);
}
.banner-content {
  position: relative;
  padding: 36px 30px;
}
.banner-title { font-size: 21px; font-weight: 700; color: #ffffff; }
.banner-sub { margin-top: 8px; font-size: 13px; color: rgba(255, 255, 255, 0.92); }

/* ===== 统计卡明细弹窗列表（种植物/设备/建议/健康共用）：行 hover 高亮，不套盒子（避免浮层内再嵌卡） ===== */
.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 12px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: background-color 0.3s ease-in-out;
}
.stat-item:hover { background: #eef2ec; }
.h-left { flex: 1; min-width: 0; }
.h-name { font-size: 15px; font-weight: 600; color: var(--neu-ink); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.h-desc { margin-top: 6px; font-size: 13px; color: var(--neu-ink2); line-height: 1.6; }

/* ===== 天气卡：整条凹陷槽承载 3 列，细分隔线 ===== */
.weather-head { display: flex; align-items: center; justify-content: space-between; }
.weather-title { font-weight: 600; color: var(--neu-ink); font-size: 15px; }
.weather-days {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  background: var(--well);
  border-radius: 16px;
  box-shadow: none;
}
.weather-day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 14px 8px;
  color: var(--neu-ink2);
}
.weather-day + .weather-day {
  border-left: 1px solid rgba(60, 84, 66, 0.08);
}
.weather-day.today .w-date { color: #3f7d4c; }
.w-date { font-size: 13px; font-weight: 700; color: var(--neu-ink); }
.w-sub { font-weight: 400; color: var(--neu-ink3); font-size: 11px; margin-left: 6px; }
.w-icon { font-size: 28px; line-height: 1; }
.w-text { font-size: 13px; }
.w-temp { font-size: 15px; font-weight: 700; color: #ff6b6b; }
.w-wind { font-size: 12px; color: var(--neu-ink3); }

/* ===== 今日农事清单 ===== */
.today-head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.today-title { font-weight: 600; color: var(--neu-ink); font-size: 15px; }
.today-date { font-weight: 400; color: var(--neu-ink3); font-size: 13px; margin-left: 8px; }
.today-right { display: flex; align-items: center; gap: 14px; }
.today-stat { font-size: 13px; color: var(--neu-ink2); }
.today-stat b { color: #f5a623; font-size: 15px; }
.today-stat.done b { color: #52ae5e; }
.today-list { margin-top: 10px; display: flex; flex-direction: column; }
.today-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: 12px;
  transition: background-color 0.3s ease-in-out, opacity 0.3s ease-in-out;
}
.today-item:hover { background: #eef2ec; }
.today-item.done { opacity: 0.55; }
.today-item.done .t-title { text-decoration: line-through; color: var(--neu-ink3); }
.t-time { font-size: 13px; font-weight: 700; color: #3f7d4c; min-width: 44px; }
.t-time.overdue { color: #ff6b6b; }
.t-title { font-size: 13px; color: var(--neu-ink); }
.t-overdue { font-size: 11px; color: #ff6b6b; font-weight: 600; }
.t-content { font-size: 12px; color: var(--neu-ink3); flex: 1; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ===== 盆栽卡片 ===== */
.plant-card {
  cursor: pointer;
  transition: transform 0.3s ease-in-out, box-shadow 0.3s ease-in-out;
}
.plant-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-card-hover); }
.plant-photo {
  position: relative;
  height: 150px;
  border-radius: 14px;
  overflow: hidden;
  background: var(--well);
  box-shadow: none;
  padding: 5px;
}
.photo-img { width: 100%; height: 100%; border-radius: 10px; display: block; }
.photo-emoji { font-size: 60px; text-align: center; line-height: 138px; }
.health-tag { position: absolute; top: 11px; right: 11px; }
.plant-body { padding-top: 12px; }
.plant-name { font-weight: 600; color: var(--neu-ink); font-size: 15px; }
.plant-species { font-size: 12px; color: var(--neu-ink2); margin-top: 3px; }
.plant-metrics { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-top: 10px; }
.pm {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 7px 0 6px;
  background: var(--well);
  border-radius: 12px;
  box-shadow: none;
}
.pm-val { font-size: 14px; font-weight: 700; color: var(--neu-ink); }
.pm-val.warn { color: #ff6b6b; }
/* 离线占位：设备离线时不显示旧数据 */
.pm-val.off { color: var(--neu-ink3); font-weight: 500; }
.pm-lbl { margin-top: 2px; font-size: 11px; color: var(--neu-ink3); }
.plant-foot { margin-top: 10px; display: flex; align-items: center; justify-content: space-between; }
.pf-dev { font-size: 12px; color: var(--neu-ink3); }

/* 添加占位：浅井 + 极淡虚线（空置待填语义） */
.add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 306px;
  color: var(--neu-ink3);
  background: var(--well);
  border: 1.5px dashed #ccd8c2;
  cursor: pointer;
  transition: background-color 0.3s ease-in-out, border-color 0.3s ease-in-out;
}
.add-card:hover { background: var(--well-hover); border-color: #b5c6a5; }

/* ===== 抽屉内容（白底浮层，块件用浅井面分层） ===== */
.drawer-photo { width: 100%; height: 200px; border-radius: 12px; }
.drawer-emoji {
  font-size: 84px;
  text-align: center;
  background: var(--well);
  border-radius: 14px;
  height: 200px;
  line-height: 200px;
  box-shadow: none;
}
.info-line { font-size: 13px; color: var(--neu-ink2); margin-top: 8px; line-height: 1.6; }
.info-line.tips { background: #eaf6ec; padding: 8px 10px; border-radius: 10px; color: #40884a; }
.info-line.diseases { color: #f5a623; }
.species-box {
  background: var(--well);
  padding: 12px 14px;
  border-radius: 14px;
  margin-top: 10px;
  box-shadow: none;
}
.sb-title { font-weight: 600; color: var(--neu-ink); margin-bottom: 4px; font-size: 13px; }

.chart-head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; font-weight: 600; color: var(--neu-ink); font-size: 13px; margin-bottom: 10px; }
.chart-slot {
  background: var(--well);
  border-radius: 16px;
  padding: 12px 10px 8px;
  box-shadow: none;
}
.advice-head { display: flex; align-items: center; justify-content: space-between; font-weight: 600; color: var(--neu-ink); font-size: 13px; margin-bottom: 10px; }
.advice-item { border-radius: 12px; padding: 10px 12px; margin-bottom: 6px; transition: background-color 0.3s ease-in-out; }
.advice-item:hover { background: #eef2ec; }
.advice-head-row { display: flex; align-items: center; justify-content: space-between; }
.advice-time { font-size: 12px; color: var(--neu-ink3); }
.advice-title { font-weight: 600; color: var(--neu-ink); margin-top: 6px; font-size: 13px; }
.advice-content { font-size: 12px; color: var(--neu-ink2); margin-top: 4px; margin-bottom: 8px; line-height: 1.6; }

.upload-box {
  width: 100px;
  height: 100px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
  background: var(--well);
  box-shadow: none;
  transition: background-color 0.3s ease-in-out;
}
.upload-box:hover { background: var(--well-hover); }
.upload-img { width: 100%; height: 100%; object-fit: cover; }
.upload-empty { color: var(--neu-ink2); font-size: 12px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.dev-tip { font-size: 12px; color: var(--neu-ink2); margin-top: 4px; line-height: 1.6; }
.stage-tag { display: inline-block; font-size: 12px; padding: 1px 10px; border-radius: 999px; background: var(--el-color-success-light-9); color: var(--el-color-success-dark-2); margin-left: 6px; }
.stage-overdue { background: var(--el-color-danger-light-9); color: var(--el-color-danger-dark-2); }
.watering-bar { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-radius: 12px; margin-bottom: 12px; font-size: 13px; }
.w-label { font-weight: 700; white-space: nowrap; }
.w-msg { color: inherit; }
.w-urgent { background: var(--el-color-danger-light-9); color: var(--el-color-danger-dark-2); }
.w-critical { background: var(--el-color-warning-light-9); color: var(--el-color-warning-dark-2); }
.w-ok { background: var(--el-color-success-light-9); color: var(--el-color-success-dark-2); }
.w-wet { background: var(--well); color: var(--neu-ink2); }
.w-rain { background: var(--el-color-primary-light-9); color: var(--el-color-primary-dark-2); }
.w-nodata { background: var(--well); color: var(--neu-ink2); }
.stage-block {
  background: var(--well);
  padding: 12px 14px;
  border-radius: 14px;
  margin-bottom: 12px;
  box-shadow: none;
}
.stage-day { font-weight: 400; color: var(--neu-ink2); font-size: 12px; margin-left: 6px; }
.stage-desc { font-size: 12px; color: var(--neu-ink2); margin-top: 8px; line-height: 1.6; }
.stage-next { font-size: 12px; color: #52ae5e; margin-top: 6px; }
/* 超期提示：整套生长周期走完才出现。配色跟卡片上 .stage-overdue 那个红 tag 保持一致， */
.stage-overdue-tip { font-size: 12px; color: var(--el-color-danger-dark-2); background: var(--el-color-danger-light-9); border: 1px solid var(--el-color-danger-light-7); border-radius: 6px; padding: 8px 10px; margin-top: 10px; line-height: 1.6; }
.record-head { display: flex; align-items: center; justify-content: space-between; font-weight: 600; color: var(--neu-ink); font-size: 13px; margin-bottom: 10px; }
.block-title { font-weight: 600; color: var(--neu-ink); font-size: 13px; }
</style>
