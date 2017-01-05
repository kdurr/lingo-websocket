var KEYCODE_BACKSPACE = 8;
var KEYCODE_RETURN = 13;

var HEIGHT = 300;
var WIDTH = 250;
var SIDE = 50;
var MARGIN_TOP = 100;
var MARGIN_BOTTOM = 75;

var myScore = 0;
var myGuess;
var myGuesses;
var myProgress;
var myResults;
var myUsername;
var opponentScore = 0;
var opponentResults;
var opponentUsername;
var lastWord;

var canvasDiv = document.getElementById('canvasDiv');
var waitingDiv = document.getElementById('waitingDiv');
var messageDiv = document.getElementById('messageDiv');
var canvas = document.getElementById('canvas');
var ctx = canvas.getContext('2d');

var client;

function main() {
	var usernameDiv = document.getElementById('usernameDiv');
	var submitUsernameFunction = function() {
		myUsername = usernameInput.value;
		localStorage.setItem('lingo.username', myUsername);
		console.log('My username: ' + myUsername);
		start();
		usernameDiv.classList.add('hidden');
		waitingDiv.classList.remove('hidden');
		messageDiv.classList.remove('hidden');
	}
	var usernameInput = document.getElementById('username');
	var usernameButton = document.getElementById('usernameButton');
	usernameButton.addEventListener('click', submitUsernameFunction);
	usernameInput.focus();
	usernameInput.addEventListener('keydown', function(e) {
		if (e.keyCode === KEYCODE_RETURN) {
			e.preventDefault();
			submitUsernameFunction();
		}
	});
	var storedUsername = localStorage.getItem('lingo.username');
	if (storedUsername === null) {
		usernameInput.value = 'Alex Trebek';
	} else {
		usernameInput.value = storedUsername;
	}
}

function start() {
	ctx.font = '25px Monospace';
	ctx.textBaseline = 'middle';
	ctx.textAlign = 'center';

	addKeydownListener();
	addKeypressListener();
	addChatMessageListener();

	reset();
	repaint();

	client = Stomp.over(new SockJS('/stomp'));

	client.connect({}, function(frame) {
		subscribeToChatMessages();
		subscribeToOpponentJoined();
		subscribeToOpponentLeft();
		subscribeToOpponentReports();
		subscribeToPlayerReports();
		client.send('/app/lingo/join', {}, myUsername);
	});
}

// special keys
function addKeydownListener() {
	canvasDiv.addEventListener('keydown', function(e) {
		if (e.which === KEYCODE_BACKSPACE) {
			myGuess = myGuess.substr(0, myGuess.length - 1);
			repaint();
			e.preventDefault();
		}
		else if (e.which === KEYCODE_RETURN) {
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
	canvasDiv.addEventListener('keypress', function(e) {
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

function addChatMessageListener() {
	var messageInput = document.getElementById('messageInput');
	messageInput.addEventListener('keydown', function(e) {
		if (e.which === KEYCODE_RETURN) {
			var text = messageInput.value;
			messageInput.value = '';
			client.send('/app/lingo/chat', {}, text);
			addChatMessage('Me', text);
		}
	});
}

function addChatMessage(sender, body) {
	var messageList = document.getElementById('messageList');
	var usernameNode = document.createElement('strong');
	var usernameTextNode = document.createTextNode(sender)
	usernameNode.appendChild(usernameTextNode);
	var messageTextNode = document.createTextNode(' ' + body);
	var chatMessage = document.createElement('div');
	chatMessage.setAttribute('class', 'list-group-item');
	chatMessage.appendChild(usernameNode);
	chatMessage.appendChild(messageTextNode);
	messageList.appendChild(chatMessage);

	// Auto-scroll if necessary
	messageList.scrollTop = messageList.scrollHeight;
}

function drawMyBoard() {
	var x = 25, y = MARGIN_TOP;
	drawUsername(x, y, myUsername);
	drawScore(x, y, myScore);
	drawInput(x, y, myGuess);
	var yStart = drawGuesses(x, y, myGuesses, myResults);
	drawHint(x, yStart, myProgress);
	drawGrid(x, y);
}

function drawOpponentBoard() {
	var x = 325, y = MARGIN_TOP;
	drawUsername(x, y, opponentUsername);
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

function drawUsername(x, y, username) {
	var usernameX = x + WIDTH / 2;
	var usernameY = y - 60;
	ctx.fillStyle = 'black';
	ctx.fillText(username, usernameX, usernameY);
}

function drawScore(x, y, score) {
	var scoreX = x + WIDTH / 2;
	var scoreY = y - 25;
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

function reset(firstLetter, clearScore) {
	if (!firstLetter) {
		firstLetter = '';
	}
	myGuess = '';
	myGuesses = [];
	myProgress = [firstLetter, '', '', '', ''];
	myResults = [];
	opponentResults = [];
	if (clearScore) {
		myScore = 0;
		opponentScore = 0;
	}
}

function subscribeToChatMessages() {
	client.subscribe('/topic/lingo/chat', function(message) {
		var chatMessage = JSON.parse(message.body);
		var messageSender = chatMessage.username;
		var messageBody = chatMessage.message;
		if (messageSender === myUsername) {
			console.log('Ignoring message sent by myself')
		} else {
			console.log('Message from ' + messageSender + ": " + messageBody);
			addChatMessage(messageSender, messageBody);
		}
	});
}

function subscribeToOpponentJoined() {
	client.subscribe('/user/topic/lingo/opponentJoined', function(message) {
		var report = JSON.parse(message.body);
		var firstLetter = report[0];
		opponentUsername = report[1];
		console.log('Opponent username: ' + opponentUsername);
		reset(firstLetter, true);
		canvasDiv.classList.remove('hidden');
		waitingDiv.classList.add('hidden');
		repaint();
	});
}

function subscribeToOpponentLeft() {
	client.subscribe('/user/topic/lingo/opponentLeft', function(message) {
		opponentUsername = null;
		lastWord = null;
		canvasDiv.classList.add('hidden');
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
			reset(firstLetter, false);
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
			reset(firstLetter, false);
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
