declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

declare module '@vue-office/pdf/lib/v3/vue-office-pdf.mjs' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent
  export default component
}

declare module '@vue-office/docx/lib/v3/vue-office-docx.mjs' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent
  export default component
}
