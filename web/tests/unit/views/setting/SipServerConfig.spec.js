import { shallowMount } from '@vue/test-utils'
import SipServerConfig from '@/views/setting/SipServerConfig.vue'

const baseConfig = {
  id: '41010500002000000001',
  domain: '4101050000',
  password: 'admin123',
  port: 5060,
  ip: '0.0.0.0',
  showIp: '192.168.1.3',
  alarm: true,
  timeout: 1000,
  runtimePort: 5060,
  runtimeIp: '0.0.0.0',
  runtimeShowIp: '192.168.1.3',
  runtimeMonitorIps: ['0.0.0.0'],
  profileId: 'dev',
  filePath: '/tmp/application-dev.yml',
  writable: true,
  restartRequiredFields: ['port', 'ip', 'showIp', 'timeout', 'alarm']
}

function mountPanel(config = baseConfig) {
  const dispatch = jest.fn(action => {
    if (action === 'setting/getSipServerConfig') {
      return Promise.resolve(config)
    }
    return Promise.resolve({ data: { appliedFields: ['id', 'domain', 'password'], restartRequiredFields: [], restartRequired: false } })
  })
  const wrapper = shallowMount(SipServerConfig, {
    mocks: {
      $store: { dispatch },
      $message: { success: jest.fn(), error: jest.fn(), info: jest.fn() },
      $confirm: jest.fn(() => Promise.reject('cancel'))
    },
    directives: { loading: () => {} },
    stubs: {
      'el-card': true,
      'el-form': {
        template: '<form><slot /></form>',
        methods: {
          validate(callback) {
            const vm = this.$parent && this.$parent.$parent
            const fields = Object.keys(vm.rules || {})
            const errors = []
            fields.forEach(field => {
              const rules = vm.rules[field] || []
              rules.forEach(rule => {
                rule.validator(rule, vm.form[field], error => {
                  if (error) {
                    errors.push({ field, message: error.message })
                  }
                })
              })
            })
            callback(errors.length === 0)
          }
        }
      },
      'el-form-item': { template: '<div><slot name="label" /><slot /></div>' },
      'el-input': { template: '<input />' },
      'el-switch': true,
      'el-divider': true,
      'el-button': { template: '<button><slot /></button>' },
      'el-alert': true,
      'restart-field-label': true,
      'restart-tag-toggle': true
    }
  })
  return { wrapper, dispatch }
}

describe('SipServerConfig', () => {
  it('shows the current plaintext password by default and allows toggling visibility', async () => {
    const { wrapper } = mountPanel()
    await wrapper.vm.$nextTick()
    await new Promise(resolve => setImmediate(resolve))

    expect(wrapper.vm.form.password).toBe('admin123')
    expect(wrapper.vm.passwordVisible).toBe(true)
    wrapper.vm.passwordVisible = false
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.passwordVisible).toBe(false)
  })

  it('fills domain from the first 10 digits of id when domain is empty', async () => {
    const { wrapper } = mountPanel({ ...baseConfig, domain: '' })
    await new Promise(resolve => setImmediate(resolve))

    expect(wrapper.vm.form.domain).toBe('4101050000')
  })

  it('tracks id changes while domain still follows the old id prefix', async () => {
    const { wrapper } = mountPanel()
    await new Promise(resolve => setImmediate(resolve))

    wrapper.vm.form.id = '42010600002000000002'
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.form.domain).toBe('4201060000')
  })

  it('does not overwrite a manually edited domain', async () => {
    const { wrapper } = mountPanel()
    await new Promise(resolve => setImmediate(resolve))

    wrapper.vm.form.domain = '9999999999'
    wrapper.vm.form.id = '42010600002000000002'
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.form.domain).toBe('9999999999')
  })

  it('rejects empty password before save', async () => {
    const { wrapper, dispatch } = mountPanel()
    await new Promise(resolve => setImmediate(resolve))

    wrapper.vm.form.password = '   '
    wrapper.vm.form.id = '41010500002000000002'
    await wrapper.vm.handleSave()
    await new Promise(resolve => setImmediate(resolve))

    expect(dispatch).not.toHaveBeenCalledWith('setting/saveSipServerConfig', expect.anything())
  })

  it('reports applied identity fields and restart fields separately', async () => {
    const { wrapper, dispatch } = mountPanel()
    await new Promise(resolve => setImmediate(resolve))
    wrapper.vm.form.password = 'new-password'
    dispatch.mockImplementation(action => {
      if (action === 'setting/getSipServerConfig') {
        return Promise.resolve(baseConfig)
      }
      return Promise.resolve({
        data: {
          appliedFields: ['id', 'domain', 'password'],
          restartRequiredFields: ['port'],
          restartRequired: true
        }
      })
    })

    await wrapper.vm.doSave(false)

    expect(wrapper.vm.appliedFields).toEqual(['id', 'domain', 'password'])
    expect(wrapper.vm.restartFieldKeys).toEqual(['port'])
    expect(wrapper.vm.$message.success).toHaveBeenCalledWith(
      expect.stringContaining('身份字段已对后续请求生效')
    )
  })
})
