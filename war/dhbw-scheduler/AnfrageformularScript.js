$('document').ready(function(){		
	$('#btnSubmit').on('click',function(){
		var objData={timeTableArray:formatArray(),datelist:getDatelist()};
		var url=$('#inpHidden').val();
		$.ajax({
			 url: url,
		     type: 'POST',
		     data: objData,
		     success: function (data){
		    	 alert("Gesendet");
		     },
		     error: function(){
		    	 alert("Error");
		     }		
		});
	})
	//Selection Funktion
	$('#timeTableBody td').on('click',function(){
		if($(this).attr('class') != 'tdSelect'){
			$(this).removeClass();
			$(this).addClass('tdSelect');
		}
		else{
			if($(this).html() == '+'){
				$(this).removeClass();
				$(this).addClass('tdPlus');
			}
			else if($(this).html() == '-'){
				$(this).removeClass();
				$(this).addClass('tdMinus');
			}
			else{
				$(this).removeClass();
				$(this).addClass('tdNeutral');
			}
		}
	});
	//Datum der Liste hinzufügen, falls noch nicht vorhanden
	$('#btnSetDate').on('click',function(){
		var write= true;
		var value=$('#inpDatepicker').val();
		$('#dateList li').each(function(){
			if($(this).html() == value){
				write= false;
				alert("Datum: "+$(this).html()+" ist bereits vorhanden!");
			}
		});
		if(write ==true){
			var item='<li>'+value+'</li>';
			$('#dateList').append(item);
		}
	});
	//Markiert alle ausgewählten Zellen mit +
	$('#btnPlus').on('click',function(){
		var obj=getSelectedTd();
		for(var i in obj){
			obj[i].removeClass();
			obj[i].addClass('tdPlus');
			obj[i].html('+');
		}
	});
	//Markiert alle ausgewählten Zellen mit -
	$('#btnMinus').on('click',function(){
		var obj=getSelectedTd();
		for(var i in obj){
			obj[i].removeClass();
			obj[i].addClass('tdMinus');
			obj[i].html('-');
		}
	});
	//Macht alle ausgewählten Zellen leer
	$('#btnClear').on('click',function(){
		var obj=getSelectedTd();
		for(var i in obj){
			obj[i].removeClass();
			obj[i].addClass('tdNeutral');
			obj[i].html('');
		}
	});
			
});
//Befüllt die Stundentabelle dynamisch
function fillTimeTable(){				
	var tbody='';
	for(var i=8;i<18;i++){
		tbody+='<tr>';
		tbody+='<th>'+i+'.00 - '+(i+1)+'.00</th>';
		tbody+='<td></td>';
		tbody+='<td></td>';
		tbody+='<td></td>';
		tbody+='<td></td>';
		tbody+='<td></td>';
		tbody+='<td></td>';
		tbody+='</tr>';
	}					
	return tbody;
}
//Gibt alle ausgewählten Zellen der Stundentabelle
function getSelectedTd(){
	var counter=0;
	var selectedTds=new Array();
	$('#timeTableBody td').each(function(){
		if($(this).attr('class') == "tdSelect"){
			selectedTds[counter]=$(this);
			counter++;
		}
	});
	return selectedTds;
}
//Liest alle Zellen der Timetable aus ( "-" = -1,"+"=1,""=0)
function getTimeTableVal(){
	var tableArray = new Array();
	var currVal=0;
	var rowCounter=0;
	$('#timeTableBody tr').each(function(){
		var rowArray = new Array();
		var tdCounter=0;
		$(this).find('td').each(function(){
			if($(this).html() == '+'){
				currVal=1;
			}
			else if($(this).html() == '-'){
				currVal=-1;
			}
			else{
				currVal=0;
			}
			rowArray[tdCounter] = currVal;
			tdCounter++;
		});
		tableArray[rowCounter] = rowArray;
		rowCounter++;
	});
	return tableArray;
}
//Formatiert das Array 
function formatArray(){
	var tableArray = getTimeTableVal();
	var taFdLength = tableArray.length;
	var newTableArray = new Array();
	var startTime = parseInt($('#timeTableBody tr').first().find('th').html().split('.')[0]); //Start Uhrzeit der Tabelle ( 8.00-9.00 = 8)				
	for(var i=0;i<6;i++){
		newTableArray[i] = new Array();
		for(var j=0;j<taFdLength;j++){
			var tempT= startTime+j;
			newTableArray[i][tempT]=tableArray[j][i];
		}
	}				
	return newTableArray;
}
//Holt die Daten der Ausnahmen
function getDatelist(){
	var dateArray=new Array();
	var counter=0;
	$('#dateList li').each(function(){
		dateArray[counter]=$(this).html();
		counter++;
	});
	return dateArray;
}