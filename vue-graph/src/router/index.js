import Vue from 'vue'
import VueRouter from 'vue-router'
import GanttChart from '../views/GanttChart.vue'
import StructureGraph from '../views/StructureGraph.vue'

Vue.use(VueRouter)

const routes = [
    {
        path: '/',
        redirect: '/structure-graph' // default route
    },
    {
        path: '/gantt-chart',
        name: 'GanttChart',
        component: GanttChart
    },
    {
        path: '/structure-graph',
        name: 'StructureGraph',
        component: StructureGraph
    }
]

const router = new VueRouter({
    mode: 'history',
    base: process.env.BASE_URL,
    routes
})

export default router
