var HEIGHT = 300;
var WIDTH = 250;
var SIDE = 50;
var MARGIN_TOP = 50;
var MARGIN_BOTTOM = 75;

var myScore = 0;
var myGuess;
var myGuesses;
var myProgress;
var myResults;
var opponentScore = 0;
var opponentResults;
var lastWord;

var canvasDiv = document.getElementById('canvasDiv');
var waitingDiv = document.getElementById('waitingDiv');
var canvas = document.getElementById('canvas');
var ctx = canvas.getContext('2d');

var client;

function main() {
	ctx.font = '25px Monospace';
	ctx.textBaseline = 'middle';
	ctx.textAlign = 'center';

	addKeydownListener();
	addKeypressListener();

	reset();
	repaint();

	client = Stomp.over(new SockJS('/sockjs'));

	client.connect({}, function(frame) {
		console.log('Connected: ' + frame);
		subscribeToOpponentJoined();
		subscribeToOpponentLeft();
		subscribeToOpponentReports();
		subscribeToPlayerReports();
		client.send('/app/lingo/join');
	});
}

// special keys
function addKeydownListener() {
	document.addEventListener('keydown', function(e) {
		// backspace
		if (e.which === 8) {
			myGuess = myGuess.substr(0, myGuess.length - 1);
			repaint();
			e.preventDefault();
		}
		// return
		else if (e.which === 13) {
			if (myGuess.length === 5) {
				client.send("/app/lingo/guess", {}, myGuess);
				myGuess = '';
				repaint();
			}
		}
	});
}

// characters
function addKeypressListener() {
	document.addEventListener('keypress', function(e) {
		var charCode = e.charCode;
		if (isCharacter(charCode)) {
			if (isCharacterLowercase(charCode)) {
				charCode = charCode - 32;
			}
			var char = String.fromCharCode(charCode);
			if (myGuess.length < 5) {
				myGuess += char;
				repaint();
			}
		}
	});
}

function drawMyBoard() {
	var x = 25, y = MARGIN_TOP;
	drawScore(x, y, myScore);
	drawInput(x, y, myGuess);
	var yStart = drawGuesses(x, y, myGuesses, myResults);
	drawHint(x, yStart, myProgress);
	drawGrid(x, y);
}

function drawOpponentBoard() {
	var x = 325, y = MARGIN_TOP;
	drawScore(x, y, opponentScore);
	drawResults(x, y, opponentResults);
	drawGrid(x, y);
}

function drawLastWord() {
	if (lastWord) {
		var x = canvas.width / 2;
		var y = canvas.height - MARGIN_BOTTOM / 2;
		ctx.fillStyle = 'black';
		ctx.fillText('Previous word: ' + lastWord.toUpperCase(), x, y);
	}
}

function drawScore(x, y, score) {
	var scoreX = x + WIDTH / 2;
	var scoreY = y / 2;
	ctx.fillStyle = 'black';
	ctx.fillText(score, scoreX, scoreY);
}

function drawGrid(xOrigin, yOrigin) {
	ctx.beginPath();
	for (var x = 0; x <= WIDTH; x += SIDE) {
		ctx.moveTo(xOrigin + x, yOrigin);
		ctx.lineTo(xOrigin + x, yOrigin + HEIGHT);
	}
	for (var y = 0; y <= HEIGHT; y += SIDE) {
		ctx.moveTo(xOrigin, yOrigin + y);
		ctx.lineTo(xOrigin + WIDTH, yOrigin +  y);
	}
	ctx.strokeStyle = 'black';
	ctx.stroke();
}

function drawInput(xOrigin, yOrigin, input) {
	ctx.fillStyle = 'green';
	var x = xOrigin + SIDE * 0.5;
	var y = yOrigin + SIDE * 0.5;
	for (var i = 0; i < myGuess.length; i++) {
		ctx.fillText(myGuess[i], x, y);
		x += SIDE;
	}
}

function drawGuesses(xOrigin, yOrigin, guesses, results) {
	var y = yOrigin + SIDE * 1.5;
	var numGuesses = Math.min(4, guesses.length);
	for (var i = 0; i < numGuesses; i++) {
		var x = xOrigin + SIDE * 0.5;
		var guess = guesses[guesses.length - numGuesses + i];
		var result = results[results.length - numGuesses + i];
		for (var j = 0; j < 5; j++) {
			if (result[j] === 1) {
				ctx.fillStyle = 'yellow';
				ctx.fillRect(x - SIDE * 0.5, y - SIDE * 0.5, SIDE, SIDE);
			} else if (result[j] === 2) {
				ctx.fillStyle = 'orange';
				ctx.fillRect(x - SIDE * 0.5, y - SIDE * 0.5, SIDE, SIDE);
			}
			ctx.fillStyle = 'green';
			ctx.fillText(guess[j], x, y);
			x += SIDE;
		}
		y += SIDE;
	}
	return y;
}

function drawResults(xOrigin, yOrigin, results) {
	var y = yOrigin + SIDE * 1.5;
	var numResults = Math.min(4, results.length);
	for (var i = 0; i < numResults; i++) {
		var x = xOrigin + SIDE * 0.5;
		var result = results[results.length - numResults + i];
		for (var j = 0; j < 5; j++) {
			if (result[j] === 1) {
				ctx.fillStyle = 'yellow';
				ctx.fillRect(x - SIDE * 0.5, y - SIDE * 0.5, SIDE, SIDE);
			} else if (result[j] === 2) {
				ctx.fillStyle = 'orange';
				ctx.fillRect(x - SIDE * 0.5, y - SIDE * 0.5, SIDE, SIDE);
			}
			x += SIDE;
		}
		y += SIDE;
	}
	return y;
}

function drawHint(xOrigin, yOrigin, progress) {
	var x = xOrigin + SIDE * 0.5;
	for (var i = 0; i < 5; i++) {
		ctx.fillText(progress[i], x, yOrigin);
		x += SIDE;
	}
}

function isCharacter(charCode) {
	return isCharacterLowercase(charCode) || isCharacterUppercase(charCode);
}

function isCharacterLowercase(charCode) {
	return charCode >= 97 && charCode <= 122;
}

function isCharacterUppercase(charCode) {
	return charCode >= 65 && charCode <= 90;
}

function isValidResult(result) {
	for (var i = 0; i < 5; i++) {
		if (result[i] !== 0 && result[i] !== 1 && result[i] !== 2) {
			return false;
		}
	}
	return true;
}

function repaint() {
	// clear the canvas
	ctx.clearRect(0, 0, canvas.width, canvas.height);

	// draw the components
	drawMyBoard();
	drawOpponentBoard();
	drawLastWord();
}

function reset(firstLetter) {
	if (!firstLetter) {
		firstLetter = '';
	}
	myGuess = '';
	myGuesses = [];
	myProgress = [firstLetter, '', '', '', ''];
	myResults = [];
	opponentResults = [];
}

function subscribeToOpponentJoined() {
	client.subscribe('/user/topic/lingo/opponentJoined', function(message) {
		var firstLetter = message.body;
		reset(firstLetter);
		gameDiv.classList.remove('hidden');
		waitingDiv.classList.add('hidden');
		repaint();
	});
}

function subscribeToOpponentLeft() {
	client.subscribe('/user/topic/lingo/opponentLeft', function(message) {
		lastWord = null;
		gameDiv.classList.add('hidden');
		waitingDiv.classList.remove('hidden');
		repaint();
	});
}

function subscribeToOpponentReports() {
	client.subscribe('/user/topic/lingo/opponentReports', function(message) {
		var report = JSON.parse(message.body);
		if (report.correct === true) {
			var guess = report.guess;
			var firstLetter = report.firstLetter;
			console.log('Opponent guessed correctly! ' + guess);
			opponentScore = opponentScore + 100;
			lastWord = guess;
			reset(firstLetter);
			repaint();
		} else {
			var result = report.result;
			console.log('Opponent result: ' + result);
			opponentResults.push(result);
			repaint();
		}
	});
}

function subscribeToPlayerReports() {
	client.subscribe('/user/topic/lingo/playerReports', function(message) {
		var report = JSON.parse(message.body);
		console.log('My report: ' + report);
		if (report.correct === true) {
			var guess = report.guess;
			var firstLetter = report.firstLetter;
			console.log('I guessed correctly!');
			myScore = myScore + 100;
			lastWord = guess;
			reset(firstLetter);
			repaint();
		} else {
			var guess = report.guess;
			var result = report.result;
			console.log('My result: ' + result);
			// TODO: use isValidResult function
			if (result[0] === 9) {
				myGuesses.push('-----');
			} else {
				for (var i = 0; i < 5; i++) {
					if (result[i] === 2) {
						myProgress[i] = guess[i];
					}
				}
				myGuesses.push(guess);
			}
			myResults.push(result);
			repaint();
		}
	});
}

main();
