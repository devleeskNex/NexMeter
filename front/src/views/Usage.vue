<template>
  <div>
    <v-layout row wrap>
      <v-flex xs12>
        <usage-list>stest</usage-list>
      </v-flex>
    </v-layout>
  </div>
</template>

<script>
  import {Vue, Component, Prop, Watch} from 'vue-property-decorator';
  import UsageList from '@/components/UsageList'

  @Component({
    components: {
      'usage-list': UsageList
    }
  })
  export default class Units extends Vue {
    data() {
      return {}
    }

    mounted() {

    }
  }
</script>
