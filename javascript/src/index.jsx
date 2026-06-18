import React from 'react';
import { createRoot } from 'react-dom/client';
import PermalinkGeneratorApp from './PermalinkGeneratorApp';
const cfg = window.__PL_CONFIG__;
createRoot(document.getElementById('permalink-generator-root')).render(<PermalinkGeneratorApp {...cfg} />);
