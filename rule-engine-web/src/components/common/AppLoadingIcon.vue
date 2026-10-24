<template>
  <span class="tianshu-loading" aria-hidden="true" :style="{ fontSize: typeof size === 'number' ? size + 'px' : size }" />
</template>

<script>
export default {
  name: 'AppLoadingIcon',
  props: { size: { type: [String, Number], default: '1em' } },
}
</script>
