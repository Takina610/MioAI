import { Modal } from 'ant-design-vue'

interface ConfirmOptions {
  title?: string
  content?: string
  okText?: string
  cancelText?: string
}

interface MioConfirmInterface {
  confirm: (options: ConfirmOptions) => Promise<boolean>
  delete: (options: ConfirmOptions) => Promise<boolean>
  info: (options: ConfirmOptions) => Promise<boolean>
  success: (options: ConfirmOptions) => Promise<boolean>
  error: (options: ConfirmOptions) => Promise<boolean>
  warning: (options: ConfirmOptions) => Promise<boolean>
}

const MioConfirm: MioConfirmInterface = {
  confirm(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve, reject) => {
      Modal.confirm({
        title: options.title || '确认',
        content: options.content || '确定要执行此操作吗？',
        okText: options.okText || '确定',
        cancelText: options.cancelText || '取消',
        onOk: () => resolve(true),
        onCancel: () => reject(false)
      })
    })
  },

  delete(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve, reject) => {
      Modal.confirm({
        title: options.title || '确认删除',
        content: options.content || '确定要删除吗？此操作不可恢复。',
        okText: options.okText || '删除',
        cancelText: options.cancelText || '取消',
        okType: 'danger',
        onOk: () => resolve(true),
        onCancel: () => reject(false)
      })
    })
  },

  info(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      Modal.info({
        title: options.title || '提示',
        content: options.content || '',
        okText: options.okText || '知道了',
        onOk: () => resolve(true)
      })
    })
  },

  success(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      Modal.success({
        title: options.title || '成功',
        content: options.content || '',
        okText: options.okText || '确定',
        onOk: () => resolve(true)
      })
    })
  },

  error(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      Modal.error({
        title: options.title || '错误',
        content: options.content || '',
        okText: options.okText || '确定',
        onOk: () => resolve(true)
      })
    })
  },

  warning(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      Modal.warning({
        title: options.title || '警告',
        content: options.content || '',
        okText: options.okText || '确定',
        onOk: () => resolve(true)
      })
    })
  }
}

export default MioConfirm
