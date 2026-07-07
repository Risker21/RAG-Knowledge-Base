const CAPTCHA_URL = '/api/captcha/image'

export const getCaptchaUrl = () => `${CAPTCHA_URL}?t=${Date.now()}`
