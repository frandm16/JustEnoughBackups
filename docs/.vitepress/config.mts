import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Just Enough Backups',
  description: 'Fast, multithreaded world backups with an intuitive in-game GUI for Minecraft Fabric',
  base: '/',
  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/icon.png?v=20261016' }],
    ['meta', { name: 'theme-color', content: '#486b84' }]
  ],
  themeConfig: {
    logo: '/icon.png?v=20261016',
    siteTitle: 'Just Enough Backups',
    nav: [
      { text: 'Getting Started', link: '/getting-started' },
      { text: 'Commands', link: '/commands' },
      { text: 'Configuration', link: '/configuration' }
    ],
    sidebar: [
      {
        text: 'User Guide',
        items: [
          { text: 'Getting Started', link: '/getting-started' },
          { text: 'In-Game GUI & HUD', link: '/in-game-gui' },
          { text: 'Commands Reference', link: '/commands' }
        ]
      },
      {
        text: 'Core Concepts',
        items: [
          { text: 'Backup Types & Compression', link: '/backup-types' },
          { text: 'World Restoration', link: '/restoration' }
        ]
      },
      {
        text: 'Server Administration',
        items: [
          { text: 'Configuration Reference', link: '/configuration' },
          { text: 'Cloud Storage (S3 & WebDAV)', link: '/remote-storage' },
          { text: 'Troubleshooting & FAQ', link: '/troubleshooting' }
        ]
      }
    ],
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: {
                buttonText: 'Search documentation',
                buttonAriaLabel: 'Search documentation'
              },
              modal: {
                noResultsText: 'No results found',
                resetButtonTitle: 'Reset search',
                footer: {
                  selectText: 'to select',
                  navigateText: 'to navigate',
                  closeText: 'to close'
                }
              }
            }
          }
        }
      }
    },
    footer: {
      message: '<span class="footer-brand"><img src="/icon.png" alt="" /><strong>Just Enough Backups</strong></span><span class="footer-tagline">Reliable world backups for Fabric servers.</span><span class="footer-links"><a href="/getting-started">Documentation</a><a href="https://github.com/frandm16/JustEnoughBackups" target="_blank" rel="noopener noreferrer">GitHub</a><a href="https://modrinth.com/mod/justenoughbackups-jeb" target="_blank" rel="noopener noreferrer">Modrinth</a></span>',
      copyright: 'Released under the MIT License · Copyright © 2026 FranDM'
    }
  }
})
