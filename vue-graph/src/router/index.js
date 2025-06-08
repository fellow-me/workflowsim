import Vue from 'vue'
import VueRouter from 'vue-router'
import GanttChart from '../views/GanttChart.vue'
import TaskDependencyGraph from '../views/TaskDependencyGraph.vue'

Vue.use(VueRouter)

const routes = [
    {
        path: '/',
        redirect: '/task-dependency-graph' // 默认展示任务依赖图
    },
    {
        path: '/gantt-chart',
        name: 'GanttChart',
        component: GanttChart
    },
    {
        path: '/task-dependency-graph',
        name: 'TaskDependencyGraph',
        component: TaskDependencyGraph
    }
]

const router = new VueRouter({
    mode: 'history',  // 使用 history 模式替代 createWebHistory
    base: process.env.BASE_URL,
    routes
})

export default router
