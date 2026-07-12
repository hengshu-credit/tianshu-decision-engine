<template>
  <var-picker v-bind="$attrs" v-on="$listeners" :operand-mode="true" />
</template>

<script>
import VarPicker from './VarPicker.vue'

export default {
  name: 'OperandPicker',
  inheritAttrs: false,
  components: { VarPicker }
}
</script>
