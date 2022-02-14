
const addressDetailComponent = () => import('@/views/address/address-detail.vue')
const restrictingInfoComponent = () => import('@/views/address/restricting-info.vue')

export const addressDetail = {
    path: '/address-detail',
    component: addressDetailComponent,
    name: 'addressDetailComponent'
}

export const restrictingInfo = {
    path: '/restricting-info',
    component: restrictingInfoComponent,
    name: 'restrictingInfoComponent'
}
