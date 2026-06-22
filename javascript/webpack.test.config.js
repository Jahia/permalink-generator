const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
// webpack.config.js exports a function (env, argv); call it to get the config object.
const base = require('./webpack.config')({}, { mode: 'development' });

module.exports = {
    ...base,
    mode: 'development',
    devtool: 'source-map',
    output: { ...base.output, path: path.resolve(__dirname, 'dist') },
    plugins: [
        new HtmlWebpackPlugin({ template: './cypress/support/template.html', inject: 'body' })
    ],
    devServer: { port: 8888, hot: false, open: false }
};
