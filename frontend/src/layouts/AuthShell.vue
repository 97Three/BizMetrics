<script setup lang="ts">
import { brand } from '../brand'
defineProps<{ mode: 'login' | 'register' }>()
</script>

<template>
  <div class="lg-shell">
    <aside class="lg-brand">
      <div class="lg-brand-top">
        <span class="lg-mark">{{ brand.initials }}</span>
        <span class="lg-name">{{ brand.name }}</span>
        <span class="lg-sub">经营数据工作台</span>
      </div>

      <div class="lg-brand-main">
        <span class="lg-eyebrow">OPERATIONS, IN FOCUS</span>
        <h1>把经营、资金与人事<br />放进同一张表里。</h1>
        <p>多主体的收支、预算、票据与人力成本，一屏看清。每天打开工作台，就知道下一步该做什么。</p>
        <ul class="lg-proof">
          <li><b>多主体</b><span>公司与家庭分别核算</span></li>
          <li><b>可追溯</b><span>票据与流水一一对应</span></li>
          <li><b>可审计</b><span>操作留痕、权限到人</span></li>
        </ul>
      </div>

      <div class="lg-brand-foot">
        <span>© 2026 {{ brand.name }}</span>
        <span>数据仅对授权成员可见</span>
      </div>
    </aside>

    <main class="lg-form-side">
      <div class="lg-top">
        <span>{{ brand.name }} / 工作台</span>
        <RouterLink v-if="mode === 'register'" to="/login">已有账号？登录</RouterLink>
        <RouterLink v-else to="/register">创建账号 →</RouterLink>
      </div>
      <div class="lg-form-wrap"><slot /></div>
      <div class="lg-foot">安全连接 · 登录后按你的权限展示业务数据</div>
    </main>
  </div>
</template>

<style scoped>
.lg-shell{min-height:100vh;display:grid;grid-template-columns:minmax(0,1.02fr) minmax(420px,.98fr);background:#fff;color:var(--ink)}
.lg-brand{position:relative;overflow:hidden;display:flex;flex-direction:column;justify-content:space-between;
  padding:44px 56px 34px;border-right:1px solid #eef1f6;
  background:
    radial-gradient(720px 520px at 82% 18%, rgba(109,140,255,.16), transparent 62%),
    radial-gradient(620px 520px at 8% 92%, rgba(54,89,227,.10), transparent 60%),
    linear-gradient(180deg,#fbfcff,#f4f7fd)}
.lg-brand::before{content:"";position:absolute;inset:0;pointer-events:none;opacity:.5;
  background-image:linear-gradient(#dfe6f2 1px,transparent 1px),linear-gradient(90deg,#dfe6f2 1px,transparent 1px);
  background-size:56px 56px;mask-image:radial-gradient(620px 460px at 30% 30%,#000 25%,transparent 78%)}
.lg-brand-top{position:relative;display:flex;align-items:center;gap:10px}
.lg-mark{width:34px;height:34px;border-radius:10px;display:grid;place-items:center;color:#fff;font-weight:700;font-size:15px;
  background:linear-gradient(140deg,#4d7cff,#6c8cff);box-shadow:0 8px 18px -8px rgba(77,124,255,.9)}
.lg-name{font-size:15px;font-weight:650;letter-spacing:-.2px}
.lg-sub{font-size:10.5px;color:#8a97a8;border-left:1px solid #dfe6f0;padding-left:10px;margin-left:2px}
.lg-brand-main{position:relative;max-width:430px;margin:auto 0}
.lg-eyebrow{font-size:10.5px;letter-spacing:2.4px;color:#3d63d6;font-weight:600}
.lg-brand h1{font-size:40px;line-height:1.24;letter-spacing:-1.3px;font-weight:650;margin-top:16px;color:#0b1b33}
.lg-brand p{font-size:13.5px;line-height:1.85;color:#5f6f86;margin-top:16px}
.lg-proof{list-style:none;display:flex;gap:0;margin-top:32px;padding:0}
.lg-proof li{padding:0 22px;border-left:1px solid #dde5f0;display:flex;flex-direction:column;gap:5px}
.lg-proof li:first-child{padding-left:0;border-left:none}
.lg-proof b{font-size:13px;font-weight:650;color:#12233d}
.lg-proof span{font-size:11px;color:#7b8a9e}
.lg-brand-foot{position:relative;display:flex;justify-content:space-between;font-size:10.5px;color:#93a1b3;letter-spacing:.2px}
.lg-form-side{display:flex;flex-direction:column;padding:38px clamp(28px,4.4vw,64px) 26px;min-height:100vh}
.lg-top{display:flex;align-items:center;justify-content:space-between;font-size:11.5px;color:#93a1b3}
.lg-top a{color:#3d63d6;font-size:12px;text-decoration:none}
.lg-top a:hover{text-decoration:underline}
.lg-form-wrap{flex:1;display:flex;align-items:center;justify-content:center;padding:24px 0}
.lg-form-wrap :deep(.auth-panel){width:min(100%,404px)}
.lg-foot{font-size:10.5px;color:#a3b0c0;text-align:center}
@media (max-width:1000px){
  .lg-shell{grid-template-columns:1fr}
  .lg-brand{border-right:none;border-bottom:1px solid #eef1f6;padding:28px 28px 24px;min-height:0}
  .lg-brand-main{margin:26px 0 4px}
  .lg-brand h1{font-size:28px;letter-spacing:-.8px}
  .lg-brand p{display:none}
  .lg-proof{margin-top:18px;flex-wrap:wrap;gap:10px 0}
  .lg-proof li{padding:0 16px}
  .lg-brand-foot{display:none}
  .lg-form-side{min-height:0;padding:22px 24px 28px}
}
</style>
