var KEYCODE_BACKSPACE = 8;
var KEYCODE_RETURN = 13;

var HEIGHT = 300;
var WIDTH = 250;
var SIDE = 50;
var MARGIN_TOP = 100;
var MARGIN_BOTTOM = 75;

var client;
var sessionId = null;

var vm = new Vue({
	el: '#vue-app',
	data: {
		games: [],
		gameId: null,
		messages: [],
		username: null,
		usernameError: '',
		myScore: 0,
		myGuess: '',
		myGuesses: [],
		myProgress: [],
		myResults: [],
		opponentScore: 0,
		opponentResults: [],
		opponentUsername: null,
		lastWord: null
	},
	computed: {
		inGame: function() {
			return this.gameId !== null;
		},
		inStartedGame: function() {
			var game = this.getGame(this.gameId);
			return game !== null && game.started === true;
		}
	},
	directives: {
		autoscroll: {
			bind: function(element, binding) {
				var observer = new MutationObserver(scrollToBottom);
				var config = { childList: true };
				observer.observe(element, config);

				function scrollToBottom() {
					element.scrollTop = element.scrollHeight;
				}
			}
		}
	},
	methods: {
		drawMyBoard: function(ctx) {
			var x = 25, y = MARGIN_TOP;
			this.drawUsername(ctx, x, y, this.username);
			this.drawScore(ctx, x, y, this.myScore);
			this.drawInput(ctx, x, y, this.myGuess);
			var yStart = this.drawGuesses(ctx, x, y, this.myGuesses, this.myResults);
			this.drawHint(ctx, x, yStart, this.myProgress);
			this.drawGrid(ctx, x, y);
		},
		drawOpponentBoard: function(ctx) {
			var x = 325, y = MARGIN_TOP;
			this.drawUsername(ctx, x, y, this.opponentUsername);
			this.drawScore(ctx, x, y, this.opponentScore);
			this.drawResults(ctx, x, y, this.opponentResults);
			this.drawGrid(ctx, x, y);
		},
		drawLastWord: function(canvas, ctx) {
			if (this.lastWord) {
				var x = canvas.width / 2;
				var y = canvas.height - MARGIN_BOTTOM / 2;
				ctx.fillStyle = 'black';
				ctx.fillText('Previous word: ' + this.lastWord.toUpperCase(), x, y);
			}
		},
		drawUsername: function(ctx, x, y, username) {
			var usernameX = x + WIDTH / 2;
			var usernameY = y - 60;
			ctx.fillStyle = 'black';
			ctx.fillText(username, usernameX, usernameY);
		},
		drawScore: function(ctx, x, y, score) {
			var scoreX = x + WIDTH / 2;
			var scoreY = y - 25;
			ctx.fillStyle = 'black';
			ctx.fillText(score, scoreX, scoreY);
		},
		drawGrid: function(ctx, xOrigin, yOrigin) {
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
		},
		drawInput: function(ctx, xOrigin, yOrigin, input) {
			ctx.fillStyle = 'green';
			var x = xOrigin + SIDE * 0.5;
			var y = yOrigin + SIDE * 0.5;
			for (var i = 0; i < input.length; i++) {
				ctx.fillText(input[i], x, y);
				x += SIDE;
			}
		},
		drawGuesses: function(ctx, xOrigin, yOrigin, guesses, results) {
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
		},
		drawResults: function(ctx, xOrigin, yOrigin, results) {
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
		},
		drawHint: function(ctx, xOrigin, yOrigin, progress) {
			var x = xOrigin + SIDE * 0.5;
			for (var i = 0; i < 5; i++) {
				ctx.fillText(progress[i], x, yOrigin);
				x += SIDE;
			}
		},
		getGame: function(gameId) {
			for (var i = 0; i < this.games.length; i++) {
				if (this.games[i].id === gameId) {
					return this.games[i];
				}
			}
			return null;
		},
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
		},
		removeGame: function(gameId) {
			var indexToRemove = null;
			for (var i = 0; i < this.games.length; i++) {
				if (this.games[i].id === gameId) {
					indexToRemove = i;
					break;
				}
			}
			this.games.splice(indexToRemove, 1);
		},
		onCanvasKeydown: function(event) {
			if (event.which === KEYCODE_BACKSPACE) {
				event.preventDefault();
				this.myGuess = this.myGuess.substr(0, this.myGuess.length - 1);
				this.repaint();
			}
			else if (event.which === KEYCODE_RETURN) {
				if (this.myGuess.length === 5) {
					client.send("/app/guess", {}, this.myGuess);
					this.myGuess = '';
					this.repaint();
				}
			}
		},
		onCanvasKeypress: function(event) {
			var charCode = event.charCode;
			if (isCharacter(charCode)) {
				if (isCharacterLowercase(charCode)) {
					charCode = charCode - 32;
				}
				var char = String.fromCharCode(charCode);
				if (this.myGuess.length < 5) {
					this.myGuess += char;
					this.repaint();
				}
			}
		},
		onChatKeypress: function(event) {
			var messageInput = event.target;
			if (event.which === KEYCODE_RETURN) {
				// Shift+Enter -> new line
				if (!event.shiftKey) {
					event.preventDefault();
					var text = messageInput.value.trim();
					if (text.length === 0) {
						return;
					}
					messageInput.value = '';
					client.send('/app/chat', {}, text);
					addChatMessage(this.username, text);
				}
			}
		},
		repaint: function() {
			var canvas = document.getElementById('canvas');
			var ctx = canvas.getContext('2d');
			ctx.font = '25px Monospace';
			ctx.textBaseline = 'middle';
			ctx.textAlign = 'center';
			ctx.clearRect(0, 0, canvas.width, canvas.height);
			this.drawMyBoard(ctx);
			this.drawOpponentBoard(ctx);
			this.drawLastWord(canvas, ctx);
		},
		reset: function(firstLetter, clearScore) {
			if (!firstLetter) {
				firstLetter = '';
			}
			this.myGuess = '';
			this.myGuesses = [];
			this.myProgress = [firstLetter, '', '', '', ''];
			this.myResults = [];
			this.opponentResults = [];
			if (clearScore) {
				this.myScore = 0;
				this.opponentScore = 0;
			}
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

	var usernameInput = document.getElementById('nicknameInput');

	var usernameTopic = '/user/topic/sessionUsername';
	var usernameSubscription = null;
	var usernameHandler = function(message) {
		var response = JSON.parse(message.body);
		if (response.success === true) {
			console.log('Username: ' + response.username);
			vm.username = response.username;
			start();
		} else {
			vm.usernameError = response.errorMessage;
		}
	};

	usernameInput.addEventListener('keydown', function(e) {
		if (event.keyCode === KEYCODE_RETURN) {
			event.preventDefault();
			if (sessionId === null) {
				vm.usernameError = 'Not connected to server';
				return;
			}
			var usernameValue = usernameInput.value.trim();
			if (usernameValue.length === 0) {
				vm.usernameError = 'Name cannot be empty';
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
			vm.usernameError = '';
		}
	});
}

function start() {

	// Request permission to show notifications
	Notification.requestPermission().then(function(result) {
		console.log('Notification permission: ' + result);
	});

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
	client.subscribe('/user/topic/opponentJoined', onOpponentJoined);
	client.subscribe('/user/topic/opponentLeft', onOpponentLeft);
	client.subscribe('/user/topic/opponentReports', onOpponentReport);
	client.subscribe('/user/topic/playerReports', onPlayerReport);
}

function addChatAnnouncement(body) {
	vm.messages.push({
		body: body
	});
	showNotification('Announcement', body);
}

function addChatMessage(sender, body) {
	vm.messages.push({
		sender: sender,
		body: body
	});
	showNotification(sender, body);
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

function onChat(message) {
	var chatMessage = JSON.parse(message.body);
	var messageSender = chatMessage.username;
	var messageBody = chatMessage.message;
	if (messageSender === null) {
		addChatAnnouncement(messageBody);
	} else if (messageSender === vm.username) {
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
	console.log(playerOne + ' closed Game ' + gameId);
	if (playerOne === vm.username) {
		vm.gameId = null;
	}
	vm.removeGame(gameId);
}

function onGameHosted(message) {
	var game = JSON.parse(message.body);
	var gameId = game.id;
	var playerOne = game.playerOne.username;
	console.log(playerOne + ' hosted Game ' + gameId);
	vm.games.push({
		id: gameId,
		playerOne: playerOne,
		started: false
	});
	if (playerOne === vm.username) {
		vm.gameId = gameId;
	}
}

function onGameJoined(message) {
	var game = JSON.parse(message.body);
	var gameId = game.id;
	var playerOne = game.playerOne.username;
	var playerTwo = game.playerTwo.username;

	var message = playerTwo + ' joined ' + playerOne + "'s game"
	console.log(message);
	addChatAnnouncement(message);

	for (var i = 0; i < vm.games.length; i++) {
		if (vm.games[i].id === gameId) {
			vm.games[i].playerTwo = playerTwo;
			vm.games[i].started = true;
			break;
		}
	}

	if (playerTwo === vm.username) {
		vm.gameId = gameId;
	}
}

function onGameLeft(message) {
	var report = JSON.parse(message.body);
	var game = report.game;
	var gameId = game.id;
	var playerOne = game.playerOne.username;
	var gameLeaver = report.gameLeaver.username;
	console.log(gameLeaver + ' left ' + playerOne + "'s game");
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
	if (gameLeaver === vm.username) {
		vm.gameId = null;
	}
	if (previousPlayers.indexOf(vm.username) != -1) {
		onOpponentLeft();
	}
}

function onOpponentJoined(message) {
	var report = JSON.parse(message.body);
	var firstLetter = report[0];
	vm.opponentUsername = report[1];
	console.log('Opponent username: ' + vm.opponentUsername);
	vm.reset(firstLetter, true);
	vm.repaint();
}

function onOpponentLeft(message) {
	vm.opponentUsername = null;
	vm.lastWord = null;
	vm.repaint();
}

function onOpponentReport(message) {
	var report = JSON.parse(message.body);
	if (report.correct === true) {
		var guess = report.guess;
		var firstLetter = report.firstLetter;
		console.log('Opponent guessed correctly! ' + guess);
		vm.opponentScore = vm.opponentScore + 100;
		vm.lastWord = guess;
		vm.reset(firstLetter, false);
		vm.repaint();
	} else {
		var result = report.result;
		console.log('Opponent result: ' + result);
		vm.opponentResults.push(result);
		vm.repaint();
	}
}

function onPlayerReport(message) {
	var report = JSON.parse(message.body);
	console.log('My report: ' + report);
	if (report.correct === true) {
		var guess = report.guess;
		var firstLetter = report.firstLetter;
		console.log('I guessed correctly!');
		vm.myScore = vm.myScore + 100;
		vm.lastWord = guess;
		vm.reset(firstLetter, false);
		vm.repaint();
	} else {
		var guess = report.guess;
		var result = report.result;
		console.log('My result: ' + result);
		// TODO: use isValidResult function
		if (result[0] === 9) {
			vm.myGuesses.push('-----');
		} else {
			for (var i = 0; i < 5; i++) {
				if (result[i] === 2) {
					vm.myProgress[i] = guess[i];
				}
			}
			vm.myGuesses.push(guess);
		}
		vm.myResults.push(result);
		vm.repaint();
	}
}

function onUserJoined(message) {
	var report = JSON.parse(message.body);
	var username = report[0];
	var numUsers = report[1];
	if (username === vm.username) {
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

function canShowNotification() {
	if (document.hidden === 'undefined' || document.hidden === false) {
		return false;
	}
	return Notification.permission === 'granted';
}

function showNotification(messageSender, messageBody) {
	if (canShowNotification()) {
		var title = messageSender;
		var options = {
			body : messageBody,
			icon : '/chat-bubble.png'
		};
		var notification = new Notification(title, options);
		setTimeout(function() {
			notification.close();
		}, 3000);
	}
}

main();
