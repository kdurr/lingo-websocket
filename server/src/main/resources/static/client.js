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

var appDiv = document.getElementById('appDiv');
var canvasDiv = document.getElementById('canvasDiv');
var canvas = document.getElementById('canvas');
var ctx = canvas.getContext('2d');

var client;
var sessionId = null;

var vm = new Vue({
	el: '#vue-app',
	data: {
		games: [],
		gameId: null
	},
	computed: {
		inGame: function() {
			return this.gameId !== null;
		}
	},
	methods: {
		hostGame: function(event) {
			client.send('/app/hostGame');
		},
		joinGame: function(event) {
			// Discard 'game-' prefix
			var buttonId = event.target.id;
			var gameId = buttonId.substr(5);
			client.send('/app/joinGame', {}, gameId);
		},
		leaveGame: function(event) {
			client.send('/app/leaveGame');
		}
	}
});

function afterConnected(stompConnectedFrame) {
	console.log('Connected to STOMP endpoint')
	var sessionIdTopic = '/user/topic/sessionId';
	var sessionIdSubscription = null;
	var sessionIdHandler = function(message) {
		console.log('Session ID: ' + message.body);
		sessionId = message.body;
		sessionIdSubscription.unsubscribe();
	}
	sessionIdSubscription = client.subscribe(sessionIdTopic, sessionIdHandler);
}

function main() {

	client = Stomp.over(new SockJS('/stomp'));
	client.connect({}, afterConnected);

	var usernameDiv = document.getElementById('usernameDiv');
	var usernameError = document.getElementById('usernameError');
	var usernameInput = document.getElementById('nicknameInput');

	var usernameTopic = '/user/topic/sessionUsername';
	var usernameSubscription = null;
	var usernameHandler = function(message) {
		var response = JSON.parse(message.body);
		if (response.success === true) {
			console.log('Username: ' + response.username);
			myUsername = response.username;
			start();
			usernameDiv.classList.add('hidden');
			appDiv.classList.remove('hidden');
		} else {
			usernameError.innerHTML = response.errorMessage;
		}
	};

	usernameInput.focus();
	usernameInput.addEventListener('keydown', function(e) {
		if (e.keyCode === KEYCODE_RETURN) {
			e.preventDefault();
			if (sessionId === null) {
				usernameError.innerHTML = 'Not connected to server';
				return;
			}
			var usernameValue = usernameInput.value.trim();
			if (usernameValue.length === 0) {
				usernameError.innerHTML = 'Name cannot be empty';
				return;
			}
			if (usernameSubscription === null) {
				usernameSubscription = client.subscribe(usernameTopic, usernameHandler);
				client.subscribe('/topic/userJoined', onUserJoined);
			}
			client.send('/app/setUsername', {}, usernameValue);
		}
	});
	usernameInput.addEventListener('keyup', function(e) {
		if (e.keyCode === KEYCODE_RETURN) {
			return;
		}
		var usernameValue = usernameInput.value.trim();
		if (usernameValue.length !== 0) {
			usernameError.innerHTML = '';
		}
	});
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

	// Load initial data
	doHttpGet('/games', function(games) {
		for (var i = 0; i < games.length; i++) {
			var game = games[i];
			vm.games.push({
				id: game.id,
				playerOne: game.playerOne.username,
				playerTwo: game.playerTwo ? game.playerTwo.username : null,
				started: game.playerTwo !== null
			});
		}
	});

	// Subscribe to updates
	client.subscribe('/topic/chat', onChat);
	client.subscribe('/topic/gameClosed', onGameClosed);
	client.subscribe('/topic/gameHosted', onGameHosted);
	client.subscribe('/topic/gameJoined', onGameJoined);
	client.subscribe('/topic/gameLeft', onGameLeft);
	client.subscribe('/topic/gameStarted', onGameStarted);
	client.subscribe('/user/topic/opponentJoined', onOpponentJoined);
	client.subscribe('/user/topic/opponentLeft', onOpponentLeft);
	client.subscribe('/user/topic/opponentReports', onOpponentReport);
	client.subscribe('/user/topic/playerReports', onPlayerReport);
}

// special keys
function addKeydownListener() {
	canvasDiv.addEventListener('keydown', function(e) {
		if (e.which === KEYCODE_BACKSPACE) {
			e.preventDefault();
			myGuess = myGuess.substr(0, myGuess.length - 1);
			repaint();
		}
		else if (e.which === KEYCODE_RETURN) {
			if (myGuess.length === 5) {
				client.send("/app/guess", {}, myGuess);
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
	messageInput.addEventListener('keypress', function(e) {
		if (e.which === KEYCODE_RETURN) {
			// Shift+Enter -> new line
			if (!e.shiftKey) {
				e.preventDefault();
				var text = messageInput.value.trim();
				if (text.length === 0) {
					return;
				}
				messageInput.value = '';
				client.send('/app/chat', {}, text);
				addChatMessage(myUsername, text);
			}
		}
	});
}

function addChatMessage(sender, body) {
	var messageList = document.getElementById('messageList');
	var usernameNode = document.createElement('strong');
	var usernameTextNode = document.createTextNode(sender);
	usernameNode.appendChild(usernameTextNode);
	var messageTextNode = document.createTextNode(' ' + body);
	var messageItem = document.createElement('div');
	messageItem.classList.add('message-item');
	messageItem.appendChild(usernameNode);
	messageItem.appendChild(messageTextNode);
	addMessageItem(messageList, messageItem);
}

function addChatAnnouncement(body) {
	var messageList = document.getElementById('messageList');
	var messageTextNode = document.createTextNode(body);
	var messageItem = document.createElement('div');
	messageItem.classList.add('message-item');
	messageItem.classList.add('log');
	messageItem.appendChild(messageTextNode);
	addMessageItem(messageList, messageItem);
}

// Auto-scrolls the message list
function addMessageItem(messageList, messageItem) {
	if (!messageList.hasChildNodes()) {
		messageItem.classList.add('first');
	}
	messageList.appendChild(messageItem);
	messageList.scrollTop = messageList.scrollHeight;
}

function doHttpGet(url, callback) {
	var xhr = new XMLHttpRequest();
	xhr.onreadystatechange = function() {
		if (xhr.readyState === XMLHttpRequest.DONE && xhr.status === 200) {
			var response = JSON.parse(xhr.responseText);
			callback(response);
		}
	};
	xhr.open('GET', url, true);
	xhr.send();
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

function removeGame(gameId) {
	var indexToRemove = null;
	for (var i = 0; i < vm.games.length; i++) {
		if (vm.games[i].id === gameId) {
			indexToRemove = i;
			break;
		}
	}
	vm.games.splice(indexToRemove, 1);
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

function toggleView() {
	var lobbyColumn = document.getElementById('lobbyColumn');
	var gameColumn = document.getElementById('gameColumn')
	if (lobbyColumn.classList.contains('primary')) {
		lobbyColumn.classList.remove('primary');
	} else {
		lobbyColumn.classList.add('primary');
	}
	if (gameColumn.classList.contains('primary')) {
		gameColumn.classList.remove('primary');
	} else {
		gameColumn.classList.add('primary');
	}
}

function onChat(message) {
	var chatMessage = JSON.parse(message.body);
	var messageSender = chatMessage.username;
	var messageBody = chatMessage.message;
	if (messageSender === null) {
		addChatAnnouncement(messageBody);
	} else if (messageSender === myUsername) {
		// Ignore messages sent by yourself
	} else {
		console.log('Message from ' + messageSender + ": " + messageBody);
		addChatMessage(messageSender, messageBody);
	}
}

function onGameClosed(message) {
	var game = JSON.parse(message.body);
	var gameId = game.id;
	var playerOne = game.playerOne.username;
	if (playerOne === myUsername) {
		vm.gameId = null;
	}
	console.log(playerOne + ' closed Game ' + gameId);
	removeGame(gameId);
}

function onGameHosted(message) {
	var game = JSON.parse(message.body);
	var gameId = game.id;
	var playerOne = game.playerOne.username;
	if (playerOne === myUsername) {
		vm.gameId = gameId;
	}
	console.log(playerOne + ' hosted Game ' + gameId);
	vm.games.push({ id: gameId, playerOne: playerOne, started: false });
}

function onGameJoined(message) {
	var game = JSON.parse(message.body);
	var gameId = game.id;
	var playerOne = game.playerOne.username;
	var playerTwo = game.playerTwo.username;
	if (playerTwo === myUsername) {
		vm.gameId = gameId;
	}
	console.log(playerTwo + ' joined ' + playerOne + "'s game");
	for (var i = 0; i < vm.games.length; i++) {
		if (vm.games[i].id === gameId) {
			vm.games[i].playerTwo = playerTwo;
			vm.games[i].started = true;
			break;
		}
	}
	if (playerOne === myUsername || playerTwo === myUsername) {
		toggleView();
	}
}

function onGameLeft(message) {
	var report = JSON.parse(message.body);
	var game = report.game;
	var gameId = game.id;
	var playerOne = game.playerOne.username;
	var gameLeaver = report.gameLeaver.username;
	var previousPlayers = [];
	for (var i = 0; i < vm.games.length; i++) {
		if (vm.games[i].id === gameId) {
			previousPlayers.push(vm.games[i].playerOne);
			previousPlayers.push(vm.games[i].playerTwo);
			vm.games[i].playerOne = playerOne;
			vm.games[i].playerTwo = game.playerTwo ? game.playerTwo.username : null;
			vm.games[i].started = false;
			break;
		}
	}
	console.log(gameLeaver + ' left ' + playerOne + "'s game");
	if (gameLeaver === myUsername) {
		vm.gameId = null;
	}
	if (previousPlayers.indexOf(myUsername) != -1) {
		onOpponentLeft();
		toggleView();
	}
}

function onGameStarted(message) {
	var report = JSON.parse(message.body);
	var playerOne = report[0];
	var playerTwo = report[1];
	if (playerOne === myUsername) {
		addChatAnnouncement('You are playing with ' + playerTwo);
	} else if (playerTwo === myUsername) {
		addChatAnnouncement('You are playing with ' + playerOne);
	} else {
		addChatAnnouncement(playerOne + ' is playing with ' + playerTwo);
	}
}

function onOpponentJoined(message) {
	var report = JSON.parse(message.body);
	var firstLetter = report[0];
	opponentUsername = report[1];
	console.log('Opponent username: ' + opponentUsername);
	reset(firstLetter, true);
	canvasDiv.classList.remove('hidden');
	repaint();
}

function onOpponentLeft(message) {
	opponentUsername = null;
	lastWord = null;
	canvasDiv.classList.add('hidden');
	repaint();
}

function onOpponentReport(message) {
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
}

function onPlayerReport(message) {
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
}

function onUserJoined(message) {
	var report = JSON.parse(message.body);
	var username = report[0];
	var numUsers = report[1];
	if (username === myUsername) {
		addChatAnnouncement('Welcome to Lingo!');
		if (numUsers === 1) {
			addChatAnnouncement('You are the only player online');
		} else {
			addChatAnnouncement('There are ' + numUsers + ' players online');
		}
	} else {
		addChatAnnouncement(username + ' joined');
	}
}

main();
