// ESLint flat config for the Cypress tests.
// eslint-plugin-cypress >= 4 only ships flat configs, so the former .eslintrc.json
// is reproduced here; @jahia/eslint-config (eslintrc style) is loaded through FlatCompat.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {FlatCompat} from '@eslint/eslintrc';
import cypress from 'eslint-plugin-cypress';
import chaiFriendly from 'eslint-plugin-chai-friendly';
import tsParser from '@typescript-eslint/parser';

const compat = new FlatCompat({baseDirectory: path.dirname(fileURLToPath(import.meta.url))});

export default [
    {
        // The former lint script only linted TypeScript files (--ext .ts)
        ignores: [
            '**/*.js',
            '**/*.cjs',
            '**/*.mjs',
            'node_modules/',
            'results/',
            'artifacts/',
            'run-artifacts/',
            'build-dependencies/',
            'coverage/',
            'instrumented/',
            '.nyc_output/',
            'cypress/videos/'
        ]
    },
    ...compat.extends('@jahia'),
    cypress.configs.recommended,
    ...compat.extends('plugin:@typescript-eslint/recommended'),
    chaiFriendly.configs.recommendedFlat,
    {
        files: ['**/*.ts'],
        languageOptions: {
            parser: tsParser,
            // FlatCompat turns env.es6 into ecmaVersion 6; keep the former parser setting
            ecmaVersion: 2020,
            sourceType: 'module'
        },
        rules: {
            'react/boolean-prop-naming': ['error', {rule: '^((is|has)[A-Z]([A-Za-z0-9]?)+|disabled|readOnly|autoFocus)'}],
            '@typescript-eslint/no-shadow': ['error'],
            'no-warning-comments': ['error', {terms: ['fixme']}]
        }
    }
];
