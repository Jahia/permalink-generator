const path = require('path');
module.exports = {
    entry: './src/index.jsx',
    output: {
        path: path.resolve(__dirname, '../src/main/resources/javascript'),
        filename: 'permalink-generator-admin.js'
    },
    resolve: { extensions: ['.js', '.jsx'] },
    module: {
        rules: [{
            test: /\.jsx?$/,
            exclude: /node_modules/,
            use: { loader: 'babel-loader', options: {
                presets: ['@babel/preset-env', '@babel/preset-react']
            }}
        }]
    }
};
