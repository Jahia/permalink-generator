const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const base = require('./webpack.config');

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
