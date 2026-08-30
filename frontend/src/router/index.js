import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/student',
    name: 'Student',
    component: () => import('../views/Student.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: () => import('../components/StudentDashboard.vue')
      },
      {
        path: 'jobs',
        name: 'StudentJobs',
        component: () => import('../components/StudentJobs.vue')
      },
      {
        path: 'applications',
        name: 'StudentApplications',
        component: () => import('../components/StudentApplications.vue')
      }
    ]
  },
  {
    path: '/department',
    name: 'Department',
    component: () => import('../views/Department.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'DepartmentDashboard',
        component: () => import('../components/DepartmentDashboard.vue')
      },
      {
        path: 'jobs',
        name: 'DepartmentJobs',
        component: () => import('../components/DepartmentJobs.vue')
      },
      {
        path: 'applications',
        name: 'DepartmentApplications',
        component: () => import('../components/DepartmentApplications.vue')
      },
      {
        path: 'submit-job',
        name: 'SubmitJob',
        component: () => import('../components/SubmitJob.vue')
      }
    ]
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('../views/Admin.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('../components/AdminDashboard.vue')
      },
      {
        path: 'jobs',
        name: 'AdminJobs',
        component: () => import('../components/AdminJobs.vue')
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('../components/AdminUsers.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const user = localStorage.getItem('user')
  const token = localStorage.getItem('token')
  
  // 不需要登录的页面
  if (to.path === '/') {
    next()
    return
  }
  
  // 需要登录的页面
  if (user && token) {
    next()
  } else {
    next('/')
  }
})

export default router