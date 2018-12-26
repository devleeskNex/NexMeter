<template>
  <v-card>
     <highcharts style="height: 200px" v-if="loaded" :options="options"></highcharts>
    <v-card-title>
      <v-layout row>
        Usage
      </v-layout>
    </v-card-title>

    <v-data-table
      :pagination.sync="pagination"
      :headers="headers"
      :items="usage"
      :rows-per-page-items="[5,10,15,25]"
      :loading="loading"
      class="elevation-1"
    >
      <template slot="items" slot-scope="props">
        <tr>
          <td @click="move(props.item.username)" class="text-xs-right"><a>{{ props.item.username}}</a></td>
          <td class="text-xs-right">{{ props.item.basePlan }}</td>
          <td class="text-xs-right">{{ props.item.amount }}</td>
        </tr>
      </template>
    </v-data-table>
  </v-card>
</template>

<script>
  import {Vue, Component, Prop, Watch} from 'vue-property-decorator';
  import Highcharts from 'highcharts';
  Highcharts.setOptions({
    global: {
      useUTC: false
    }
  });
  
  @Component
  export default class UsageList extends Vue {
    data() {
      return {
        usage: [],
        loaded: false,
        options: {
        exporting: {enabled: false},
        title: {
          text: null
        },
        chart: {
          zoomType: 'x'
        },
        xAxis: {
          type: 'datetime'
        },
        legend: {
          enabled: false
        }
      },
        pagination: {
          rowsPerPage: 10,
          page: 1
        },
        loading: true,
        headers: [
          {text: 'Acount Id', value: 'AcountId'},
          {text: 'Base Plan', value: 'basePlan'},
          {text: 'Amount Total', value: 'amount'}
        ]
      }
    }

    mounted() {
      //this.load();
    }

    @Watch('pagination', {deep: true})
    async load() {
      this.loading = true;
      var me = this;
      var res;
      var perPage = this.pagination.rowsPerPage;
      var page = this.pagination.page;
      var offset = (page - 1) * perPage;
      var limit = perPage;
      res = await axios.get('http://13.66.252.32:24452/query?q=select%20*%20from%20record&db=meter');
      
      var result = this.influxDataConvert(res.data.results[0].series[0])
      this.loading = false;
      this.usage = result;
    }
	influxDataConvert(series) {
		var userList = {};
		var result = [];
		series.values.forEach(function(val){
			var m = {};
			series.columns.forEach(function(col, idx){
				m[col] = val[idx];
			});
			
			if (typeof userList[m.username] == "undefined") {
				userList[m.username] = {
					basePlan : m.basePlan,
					amount : m.amount
				};
			} else {
				userList[m.username].amount = userList[m.username].amount + m.amount; 
			}
		});
		Object.keys(userList).forEach(function(key){
			result.push({username: key, 
				basePlan : userList[key].basePlan, 
				amount : userList[key].amount
			});
		});
		return result;
	}
    move(userName) {
      this.loaded = false;
      this.loadUnit(userName);
    }
    
	async loadUnit(userName) {
		var host = await axios.get('http://13.66.252.32:24452/query?q=select amount from record where username=\'' + encodeURIComponent(userName) + '\' and unit = \'host\' order by time desc limit 1000&db=meter');
		var container = await axios.get('http://13.66.252.32:24452/query?q=select amount from record where username=\'' + encodeURIComponent(userName) + '\' and unit = \'container\' order by time desc limit 1000&db=meter');
		
		if (typeof host.data.results[0].series != "undefined") {
			host = host.data.results[0].series[0].values;
		} else {
			host = [];
		}
		if (typeof container.data.results[0].series != "undefined") {
			container = container.data.results[0].series[0].values;
		} else {
			container = [];
		}    
      var yAxis = [];
      var series = [];
        yAxis.push({ // Primary yAxis
          labels: { format: '{value}', style: { color: Highcharts.getOptions().colors[0] } },
          title: { text: 'container (AVG)', style: { color: Highcharts.getOptions().colors[0] } },
          opposite: true
        });
        yAxis.push({ // Primary yAxis
          labels: { format: '{value}', style: { color: Highcharts.getOptions().colors[1] } },
          title: { text: 'host (AVG)', style: { color: Highcharts.getOptions().colors[1] } },
          opposite: true
        });
        
        series.push({ type: 'area', name: "container", yAxis: 0, data: container });
        series.push({ type: 'area', name: "host", yAxis: 1, data: host });
        
      this.options.yAxis = yAxis;
      this.options.series = series;
      this.loaded = true;
    }
  }
</script>
