module.exports = {
  // 暂时关闭eslint
  lintOnSave: false,
  productionSourceMap: process.env.NODE_ENV === 'development',
  devServer: {
    port: 8080,
    open: true,
    proxy: {
      '/apis': {
        // target: 'http://192.168.120.150:40000',
        // target: "https://scan.platon.network/",
        target: 'http://127.0.0.1:50010/',
        secure: false,
        ws: true,
        changeOrigin: true,
        pathRewrite: {
          '^/apis': '/browser-server'
        }
      }
    }
  },
  transpileDependencies:['vue-particles']
}
