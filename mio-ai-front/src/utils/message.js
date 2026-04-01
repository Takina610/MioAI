import { message } from 'ant-design-vue'

const MioMessage = {
  success: (content, duration = 3) => {
    message.success({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  },

  error: (content, duration = 3) => {
    message.error({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  },

  warning: (content, duration = 3) => {
    message.warning({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  },

  info: (content, duration = 3) => {
    message.info({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  },

  loading: (content, duration = 0) => {
    return message.loading({
      content,
      duration,
      style: {
        marginTop: '60px'
      }
    })
  }
}

export default MioMessage
