<template>
  <section class="statement">
    <div class="home-container">
      <p class="statement-text hm-scroll-reveal">
        在 MioAI，我们相信 AI 的价值不止于回答问题——更在于<em>把事情真正做完</em>
      </p>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.statement {
  padding: clamp(72px, 11vw, 144px) 0;
}

.statement-text {
  font-family: var(--hm-font-display);
  font-size: clamp(26px, 4.2vw, 52px);
  font-weight: 700;
  line-height: 1.5;
  letter-spacing: -0.01em;
  color: var(--hm-text);
  max-width: 21em;

  em {
    font-style: normal;
    color: var(--hm-green);
  }
}
</style>
