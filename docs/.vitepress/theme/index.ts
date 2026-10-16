import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import './custom.css'
import GitHubStars from './components/GitHubStars.vue'
import NavDownload from './components/NavDownload.vue'

export default {
  extends: DefaultTheme,
  Layout() {
    return h(DefaultTheme.Layout, null, {
      'nav-bar-content-after': () => [
        h(GitHubStars),
        h(NavDownload)
      ]
    })
  }
}
