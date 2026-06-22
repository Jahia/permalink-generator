const path = require('path');
// Source maps only in development. Keep React bundled (no externals) — the JSP
// mounts its own standalone React root. Production must NOT emit a .map into the
// shipped module resources.
module.exports = (env, argv) => ({
    entry: './src/index.jsx',
    devtool: argv && argv.mode === 'production' ? false : 'eval-source-map',
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
});
