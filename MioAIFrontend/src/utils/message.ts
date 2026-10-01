import { message } from 'ant-design-vue'

interface MioMessageInterface {
  success: (content: string, duration?: number) => void
  error: (content: string, duration?: number) => void
  warning: (content: string, duration?: number) => void
  info: (content: string, duration?: number) => void
  loading: (content: string, duration?: number) => void
}

const MioMessage: MioMessageInterface = {
  success(content: string, duration: number = 3): void {
    message.success({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  },

  error(content: string, duration: number = 3): void {
    message.error({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  },

  warning(content: string, duration: number = 3): void {
    message.warning({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  },

  info(content: string, duration: number = 3): void {
    message.info({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  },

  loading(content: string, duration: number = 0): void {
    message.loading({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  }
}

export default MioMessage
