var clock = new THREE.Clock();
var helix_offset = 1;
var table = [
  "H", "Hydrogen", "1.00794", 1, 1,
  "He", "Helium", "4.002602", 18, 1,
  "Li", "Lithium", "6.941", 1, 2,
  "Be", "Beryllium", "9.012182", 2, 2,
  "B", "Boron", "10.811", 13, 2,
  "C", "Carbon", "12.0107", 14, 2,
  "N", "Nitrogen", "14.0067", 15, 2,
  "O", "Oxygen", "15.9994", 16, 2,
  "F", "Fluorine", "18.9984032", 17, 2,
  "Ne", "Neon", "20.1797", 18, 2,
  "Na", "Sodium", "22.98976...", 1, 3,
  "Mg", "Magnesium", "24.305", 2, 3,
  "Al", "Aluminium", "26.9815386", 13, 3,
  "Si", "Silicon", "28.0855", 14, 3,
  "P", "Phosphorus", "30.973762", 15, 3,
  "S", "Sulfur", "32.065", 16, 3,
  "Cl", "Chlorine", "35.453", 17, 3,
  "Ar", "Argon", "39.948", 18, 3,
  "K", "Potassium", "39.948", 1, 4,
  "Ca", "Calcium", "40.078", 2, 4,
  "Sc", "Scandium", "44.955912", 3, 4,
  "Ti", "Titanium", "47.867", 4, 4,
  "V", "Vanadium", "50.9415", 5, 4,
  "Cr", "Chromium", "51.9961", 6, 4,
  "Mn", "Manganese", "54.938045", 7, 4,
  "Fe", "Iron", "55.845", 8, 4,
  "Co", "Cobalt", "58.933195", 9, 4,
  "Ni", "Nickel", "58.6934", 10, 4,
  "Cu", "Copper", "63.546", 11, 4,
  "Zn", "Zinc", "65.38", 12, 4,
  "Ga", "Gallium", "69.723", 13, 4,
  "Ge", "Germanium", "72.63", 14, 4,
  "As", "Arsenic", "74.9216", 15, 4,
  "Se", "Selenium", "78.96", 16, 4,
  "Br", "Bromine", "79.904", 17, 4,
  "Kr", "Krypton", "83.798", 18, 4,
  "Rb", "Rubidium", "85.4678", 1, 5,
  "Sr", "Strontium", "87.62", 2, 5,
  "Y", "Yttrium", "88.90585", 3, 5,
  "Zr", "Zirconium", "91.224", 4, 5,
  "Nb", "Niobium", "92.90628", 5, 5,
  "Mo", "Molybdenum", "95.96", 6, 5,
  "Tc", "Technetium", "(98)", 7, 5,
  "Ru", "Ruthenium", "101.07", 8, 5,
  "Rh", "Rhodium", "102.9055", 9, 5,
  "Pd", "Palladium", "106.42", 10, 5,
  "Ag", "Silver", "107.8682", 11, 5,
  "Cd", "Cadmium", "112.411", 12, 5,
  "In", "Indium", "114.818", 13, 5,
  "Sn", "Tin", "118.71", 14, 5,
  "Sb", "Antimony", "121.76", 15, 5,
  "Te", "Tellurium", "127.6", 16, 5,
  "I", "Iodine", "126.90447", 17, 5,
  "Xe", "Xenon", "131.293", 18, 5,
  "Cs", "Caesium", "132.9054", 1, 6,
  "Ba", "Barium", "132.9054", 2, 6,
  "La", "Lanthanum", "138.90547", 4, 9,
  "Ce", "Cerium", "140.116", 5, 9,
  "Pr", "Praseodymium", "140.90765", 6, 9,
  "Nd", "Neodymium", "144.242", 7, 9,
  "Pm", "Promethium", "(145)", 8, 9,
  "Sm", "Samarium", "150.36", 9, 9,
  "Eu", "Europium", "151.964", 10, 9,
  "Gd", "Gadolinium", "157.25", 11, 9,
  "Tb", "Terbium", "158.92535", 12, 9,
  "Dy", "Dysprosium", "162.5", 13, 9,
  "Ho", "Holmium", "164.93032", 14, 9,
  "Er", "Erbium", "167.259", 15, 9,
  "Tm", "Thulium", "168.93421", 16, 9,
  "Yb", "Ytterbium", "173.054", 17, 9,
  "Lu", "Lutetium", "174.9668", 18, 9,
  "Hf", "Hafnium", "178.49", 4, 6,
  "Ta", "Tantalum", "180.94788", 5, 6,
  "W", "Tungsten", "183.84", 6, 6,
  "Re", "Rhenium", "186.207", 7, 6,
  "Os", "Osmium", "190.23", 8, 6,
  "Ir", "Iridium", "192.217", 9, 6,
  "Pt", "Platinum", "195.084", 10, 6,
  "Au", "Gold", "196.966569", 11, 6,
  "Hg", "Mercury", "200.59", 12, 6,
  "Tl", "Thallium", "204.3833", 13, 6,
  "Pb", "Lead", "207.2", 14, 6,
  "Bi", "Bismuth", "208.9804", 15, 6,
  "Po", "Polonium", "(209)", 16, 6,
  "At", "Astatine", "(210)", 17, 6,
  "Rn", "Radon", "(222)", 18, 6,
  "Fr", "Francium", "(223)", 1, 7,
  "Ra", "Radium", "(226)", 2, 7,
  "Ac", "Actinium", "(227)", 4, 10,
  "Th", "Thorium", "232.03806", 5, 10,
  "Pa", "Protactinium", "231.0588", 6, 10,
  "U", "Uranium", "238.02891", 7, 10,
  "Np", "Neptunium", "(237)", 8, 10,
  "Pu", "Plutonium", "(244)", 9, 10,
  "Am", "Americium", "(243)", 10, 10,
  "Cm", "Curium", "(247)", 11, 10,
  "Bk", "Berkelium", "(247)", 12, 10,
  "Cf", "Californium", "(251)", 13, 10,
  "Es", "Einstenium", "(252)", 14, 10,
  "Fm", "Fermium", "(257)", 15, 10,
  "Md", "Mendelevium", "(258)", 16, 10,
  "No", "Nobelium", "(259)", 17, 10,
  "Lr", "Lawrencium", "(262)", 18, 10,
  "Rf", "Rutherfordium", "(267)", 4, 7,
  "Db", "Dubnium", "(268)", 5, 7,
  "Sg", "Seaborgium", "(271)", 6, 7,
  "Bh", "Bohrium", "(272)", 7, 7,
  "Hs", "Hassium", "(270)", 8, 7,
  "Mt", "Meitnerium", "(276)", 9, 7,
  "Ds", "Darmstadium", "(281)", 10, 7,
  "Rg", "Roentgenium", "(280)", 11, 7,
  "Cn", "Copernicium", "(285)", 12, 7,
  "Uut", "Unutrium", "(284)", 13, 7,
  "Fl", "Flerovium", "(289)", 14, 7,
  "Uup", "Ununpentium", "(288)", 15, 7,
  "Lv", "Livermorium", "(293)", 16, 7,
  "Uus", "Ununseptium", "(294)", 17, 7,
  "Uuo", "Ununoctium", "(294)", 18, 7
];


var camera, scene, renderer;
var controls;

var objects = [];
var targets = { table: [], sphere: [], helix:
		[], grid: [], vmx: [] };




//$.getJSON("http://localhost:3000/model",function(data) {

// Get a listing of images from the local server
images = data.data;
//images = images.map(function(x){var x2 = x; x2.image = "http://localhost:3000/"+x2.image; return x2;});

init();
animate();

  //console.log('data is ',data);
//});


/*
var url = "http://192.168.59.3:3000/session/fb52589d-78a3-4529-bb20-27e98efc29f5/edit";
var jjj = '{"changes":[],"settings":{"max_positives":100,"max_negatives":0,"positives_order":1,"negatives_order":-1,"learn_iterations":0}}';
$.post(url,jjj,function(data) {
  console.log('data is ',data);

  images = data.data;
  //images = images.map(function(x){var x2 = x; x2.image = "http://localhost:3000/"+x2.image; return x2;});
  alert('done with post');
  init();
  animate();

},"json");
*/
/*
$.getJSON("http://localhost:3000/model",function(data) {
// Get a listing of images from the local server
images = data.data;
images = images.map(function(x){var x2 = x; x2.image = "http://localhost:3000/"+x2.image; return x2;});

init();
animate();

  //console.log('data is ',data);
});
*/

function generate_helix() {
  console.log('Generating helix');
  targets.helix = new Array();
  var vector = new THREE.Vector3();
  for ( var i2 = 0, l = objects.length; i2 < l; i2 ++ ) {
    var i = i2 + helix_offset;
    var phi = i * 0.175 + Math.PI;

    var object = new THREE.Object3D();

    object.position.x = 900 * Math.sin( phi );
    object.position.z = - ( i * 7 ) + 450;
    object.position.y = 900 * Math.cos( phi );

    //if (i == 0) {
    //  console.log(object.position.z)
    //}

    vector.x = object.position.x * 2;
    vector.z = object.position.z;
    vector.y = object.position.y * 2;

    object.lookAt( vector );

    targets.helix.push( object );

  }

  transform( targets.helix, 2000 );
  helix_offset += 1;

  setTimeout(function(){
    generate_helix();
    
  },3000);


}

function init() {

  camera = new THREE.PerspectiveCamera( 40, window.innerWidth / window.innerHeight, 1, 10000 );
  camera.position.z = 2000;

  scene = new THREE.Scene();

  // table
  var counter = 0;
  for (var qqq = 0; qqq < 3; ++qqq)
  for ( var i = 0; i < table.length; i += 5 ) {

    var element = document.createElement( 'div' );
    element.className = 'element';
    element.style.backgroundColor = 'rgba(0,127,127,.5)';
    //element.style.backgroundColor = 'rgba(0,127,127,' + ( Math.random() * 0.5 + 0.25 ) + ')';

    var number = document.createElement( 'div' );
    number.className = 'number';
    number.textContent = (i/5) + 1;
    element.appendChild( number );

    //var symbol = document.createElement( 'div' );
    //symbol.className = 'symbol';
    //symbol.textContent = table[ i ];
    //element.appendChild( symbol );

    var img = document.createElement( 'img' );
    
    img.src=images[counter].image;
    if (!img.src) {
      console.log('problemo');
    }
    //console.log('src is ',img.src);
    //img.src='http://localhost:3000/models/c585cf6c-8eb7-4c9b-be74-4b41bf69becb/image.jpg';
    img.className='symbol';
    img.style.width="100px";
    img.style.height="100px";
    //symbol.textContent = table[ i ];
    element.appendChild( img );



    var details = document.createElement( 'div' );
    details.className = 'details';
    details.innerHTML = images[counter].name;//table[ i + 1 ] + '<br>' + table[ i + 2 ];
    element.appendChild( details );

    var object = new THREE.CSS3DObject( element );
    object.position.x = Math.random() * 4000 - 2000;
    object.position.y = Math.random() * 4000 - 2000;
    object.position.z = Math.random() * 4000 - 2000;
    scene.add( object );

    objects.push( object );

    //

    var object = new THREE.Object3D();
    object.position.x = ( table[ i + 3 ] * 140 ) - 1330;
    object.position.y = - ( table[ i + 4 ] * 180 ) + 990;

    targets.table.push( object );

    counter = counter+1;
    if (counter ==
        images.length) {
      counter = 0;
    }


  }

  // sphere

  var vector = new THREE.Vector3();

  for ( var i = 0, l = objects.length; i < l; i ++ ) {

    var phi = Math.acos( -1 + ( 2 * i ) / l );
    var theta = Math.sqrt( l * Math.PI ) * phi;

    var object = new THREE.Object3D();

    object.position.x = 800 * Math.cos( theta ) * Math.sin( phi );
    object.position.y = 800 * Math.sin( theta ) * Math.sin( phi );
    object.position.z = 800 * Math.cos( phi );

    vector.copy( object.position ).multiplyScalar( 2 );

    object.lookAt( vector );

    targets.sphere.push( object );

  }

  // helix

  


  // grid

  for ( var i = 0; i < objects.length; i ++ ) {

    var object = new THREE.Object3D();

    object.position.x = ( ( i % 5 ) * 200 ) - 400;
    object.position.y = ( - ( Math.floor( i / 5 ) % 5 ) * 200 ) + 400;
    object.position.z = ( Math.floor( i / 25 ) ) * 100 - 500;

    targets.grid.push( object );

  }

  // vmx text

  var vector = new THREE.Vector3();

  for ( var i = 0, l = objects.length; i < l; i ++ ) {

    var phi = Math.acos( -1 + ( 2 * i ) / l );
    var theta = Math.sqrt( l * Math.PI ) * phi;

    var object = new THREE.Object3D();

    object.position.x = 800 * Math.cos( theta ) * Math.sin( phi );
    object.position.y = 800 * Math.sin( theta ) * Math.sin( phi );
    object.position.z = 800 * Math.cos( phi );

    vector.copy( object.position ).multiplyScalar( 2 );

    object.lookAt( vector );

    targets.vmx.push( object );

  }



  //

  renderer = new THREE.CSS3DRenderer();
  renderer.setSize( window.innerWidth, window.innerHeight );
  renderer.domElement.style.position = 'absolute';
  document.getElementById( 'container' ).appendChild( renderer.domElement );

  //

  controls = new THREE.TrackballControls( camera, renderer.domElement );
  controls.rotateSpeed = 0.5;
  controls.minDistance = 500;
  controls.maxDistance = 6000;
  controls.addEventListener( 'change', render );

  // var button = document.getElementById( 'table' );
  // button.addEventListener( 'click', function ( event ) {

  //   transform( targets.table, 2000 );

  // }, false );

  // var button = document.getElementById( 'sphere' );
  // button.addEventListener( 'click', function ( event ) {

  //   transform( targets.sphere, 2000 );

  // }, false );

  // var button = document.getElementById( 'helix' );
  // button.addEventListener( 'click', function ( event ) {

  //   //generate_helix();
  //   transform( targets.helix, 2000 );

  // }, false );

  // var button = document.getElementById( 'grid' );
  // button.addEventListener( 'click', function ( event ) {

  //   transform( targets.grid, 2000 );

  // }, false );

  // var button = document.getElementById( 'vmx' );
  // button.addEventListener( 'click', function ( event ) {

  //   transform( targets.vmx, 2000 );

  // }, false );


  //setTimeOut
  generate_helix();
  

  //

  window.addEventListener( 'resize', onWindowResize, false );

}

function transform( targets, duration ) {

  TWEEN.removeAll();
  console.log('transforming');
  for ( var i = 0; i < objects.length; i ++ ) {

    var object = objects[ i ];
    var target = targets[ i ];

    new TWEEN.Tween( object.position )
      .to( { x: target.position.x, y: target.position.y, z: target.position.z }, Math.random() * duration + duration )
      .easing( TWEEN.Easing.Exponential.InOut )
      .start();

    new TWEEN.Tween( object.rotation )
      .to( { x: target.rotation.x, y: target.rotation.y, z: target.rotation.z }, Math.random() * duration + duration )
      .easing( TWEEN.Easing.Exponential.InOut )
      .start();

  }

  new TWEEN.Tween( this )
    .to( {}, duration * 2 )
    .repeat(20)
    .onUpdate( render )
    .start();

}

function onWindowResize() {

  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();

  renderer.setSize( window.innerWidth, window.innerHeight );

  render();

}

function animate() {

  requestAnimationFrame( animate );

  TWEEN.update();

  controls.update();

}

function render() {
  renderer.render( scene, camera );

}
