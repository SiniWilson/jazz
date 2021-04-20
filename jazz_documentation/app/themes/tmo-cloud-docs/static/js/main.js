var lunrIndex, pagesIndex;

$(document).ready(function () {
  // $('#l1').width('356px');
  var totalW = window.innerWidth;


  if (totalW < 769) {

    var h = $('#mainbar').height();
    console.log('hi', h)
    $('#sidebar').css('min-height', h + 233);
    var newH = $('#sidebar').height()
    $('#mainbar').css('min-height', newH);

  }
  slider_touched = false;
  var accordions = document.getElementsByClassName('accord');

  var icons = document.getElementsByClassName('downarrow');

  for (var i = 0; i < accordions.length; i++) {
    var rot = -90;

    accordions[i].onclick = function () {


      var content = this.nextElementSibling;
      // console.log('content--',content);
      var v2 = this.childNodes[1];
      var v2Class = v2.classList;

      if (v2Class.length != 0 && v2Class[0] == 'downarrow') {


        $(v2).toggleClass('rotated');

      }

      var contentClass = content.classList;
      if (contentClass[0] != 'accord') {

        $(content).slideToggle(function () {
          // if(slider_touched == false) {
          //     $('#l1').width('317px');
          //     // slider_touched=true;
          // }
          var ht = document.getElementById('side-bar').offsetHeight;
          // var scr = document.getElementById('side-bar').scrollHeight;
          // console.log('ht = '+ht+'  ....  scr ht = '+scr);
          var assign_ht = ht;
          // var main_ht = document.getElementById('mainbar').offsetHeight;
          var main_ht = $('#mainbar').css('height');
          // console.log('main scr ht',main_ht);
          if (ht > main_ht) assign_ht = ht;
          else assign_ht = main_ht;

          $('#sidebar').css('height', assign_ht + 'px !important');

        })


      }


    }
  }

  // expandable sidebar

  var min = 300;
  var max = 3600;
  var mainmin = 200;
  var win = window.innerWidth;

  $('#split-bar').mousedown(function (e) {
    slider_touched = true;
    console.log('mousedown')
    e.preventDefault();

    $(document).mousemove(function (e) {
      console.log('mouse move')
      e.preventDefault();
      var x = e.pageX - $('#sidebar').offset().left;
      if (x > min && x < max && e.pageX < ($(window).width() - mainmin)) {
        $('#sidebar').css("width", x);

        var diff = win - x;
        var cur = $('#l1').width;
        $('#l1').width(cur - diff + 'px');


        x = document.getElementById('sidebar').offsetWidth;
        // console.log('x -> ',x);
        var y = "" + (win - x) + "px";
        $('#mainbar').css("width", y);
      }

    })
    $(document).mouseup(function (e) {
      $(document).unbind('mousemove');
    });

  });


});


function burger() {
  document.getElementById('sidebar').classList.add('visible')

}

function closesidebar() {
  document.getElementById('sidebar').classList.remove('visible')

}