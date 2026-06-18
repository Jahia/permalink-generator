import React from 'react';
import { createRoot } from 'react-dom/client';
import PermalinkGeneratorApp from './PermalinkGeneratorApp';
document.addEventListener('DOMContentLoaded', () => {
    const el = document.getElementById('permalink-generator-root');
    if (!el) return;
    createRoot(el).render(<PermalinkGeneratorApp {...window.__PL_CONFIG__} />);
});
